/*
 * Copyright 2022 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.konstructor.lsp

import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.konstructor.Config
import com.monkopedia.lsp.ClientCapabilities
import com.monkopedia.lsp.CompletionClientCapabilities
import com.monkopedia.lsp.CompletionItem
import com.monkopedia.lsp.CompletionItemTextEdit
import com.monkopedia.lsp.CompletionParams
import com.monkopedia.lsp.ConfigurationParams
import com.monkopedia.lsp.DefaultLanguageClient
import com.monkopedia.lsp.DefaultLanguageServer
import com.monkopedia.lsp.Diagnostic
import com.monkopedia.lsp.DiagnosticClientCapabilities
import com.monkopedia.lsp.DidChangeTextDocumentParams
import com.monkopedia.lsp.DidCloseTextDocumentParams
import com.monkopedia.lsp.DidOpenTextDocumentParams
import com.monkopedia.lsp.DocumentDiagnosticParams
import com.monkopedia.lsp.Hover
import com.monkopedia.lsp.HoverParams
import com.monkopedia.lsp.InitializeParams
import com.monkopedia.lsp.InitializeResult
import com.monkopedia.lsp.InitializedParams
import com.monkopedia.lsp.InsertReplaceEdit
import com.monkopedia.lsp.KsrpcLanguageClient
import com.monkopedia.lsp.KsrpcLanguageServer
import com.monkopedia.lsp.LSPAny
import com.monkopedia.lsp.PublishDiagnosticsParams
import com.monkopedia.lsp.RegistrationParams
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.SignatureHelp
import com.monkopedia.lsp.SignatureHelpParams
import com.monkopedia.lsp.TextDocumentClientCapabilities
import com.monkopedia.lsp.TextDocumentCompletionResult
import com.monkopedia.lsp.TextDocumentContentChangeEventVariant
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextDocumentItem
import com.monkopedia.lsp.TextEdit
import com.monkopedia.lsp.UnregistrationParams
import com.monkopedia.lsp.VersionedTextDocumentIdentifier
import com.monkopedia.lsp.ksrpc.LifecycleState
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonNull

/**
 * Phase 2 **real** LSP server: a delegating bridge from the nested-ksrpc
 * frontend↔backend leg to a JetBrains `kotlin-lsp` subprocess over stdio LSP.
 *
 * ```
 * frontend ⇄ (nested ksrpc) ⇄ BridgeLanguageServer ⇄ (stdio LSP / asLspConnection) ⇄ kotlin-lsp
 *                              \__ forwarder client pushes diagnostics back up _/
 * ```
 *
 * - [frontendClient] is the editor's [KsrpcLanguageClient], passed as a ksrpc param in
 *   `KonstructionService.lsp(client)`. Diagnostics are pushed to it as
 *   `publishDiagnostics` (after rewriting the wrapped-`.kt` URI back to the frontend's
 *   document URI).
 * - The subprocess-facing stub is obtained from [KotlinLspProcess.connect], passing
 *   [Forwarder] as the subprocess's client.
 * - Requests are **delegated** to the subprocess stub. Diagnostic ranges coming back are
 *   translated from WRAPPED-`.kt` space down to csgs space (Phase 3 / #38) via
 *   [DiagnosticTranslation]. Completion/hover/signatureHelp (Phase 4 / #39) are
 *   request/response: the cursor [com.monkopedia.lsp.Position] is translated UP (csgs →
 *   wrapped) on the way in, and any range in the response (completion `textEdit`, hover
 *   `range`) is translated back DOWN to csgs space on the way out — same
 *   [DiagnosticTranslation] header-line offset, so they cannot drift.
 *
 * **Event-driven pull→push diagnostics bridge (#43).** kotlin-lsp does NOT proactively
 * push `publishDiagnostics`; it is a LSP 3.17 PULL-mode server (answers
 * `textDocument/diagnostic`, signals "re-pull" via `workspace/diagnostic/refresh`).
 * kodemirror's editor client, however, listens for PUSHED diagnostics (the Phase 1
 * design). The bridge therefore turns pulls into pushes — but EVENT-DRIVEN, not by blind
 * polling. The machinery lives in [PullDiagnosticsPublisher]:
 *  - Pull-mode is detected authoritatively from the subprocess
 *    [InitializeResult]'s [ServerCapabilities.diagnosticProvider] (non-null ⇒ pull mode;
 *    there is no push capability flag). The publisher is only created when pull-mode.
 *  - Pulls are triggered on didOpen + debounced didChange, and on the engine's
 *    `workspace/diagnostic/refresh` — overridden on the backend [Forwarder] (the
 *    `DefaultLanguageClient` base THROWS for it; the frontend client's is a harmless
 *    no-op, a different object) to re-pull every open doc. A bounded cold-index backoff
 *    covers only the FIRST pull before the index warms.
 *  - `previousResultId` is threaded per doc; an `unchanged` report does not republish
 *    (only a `full` report emits `publishDiagnostics`).
 *
 * If kotlin-lsp ever also pushes, the [Forwarder] forwards those too — they coexist.
 *
 * Lifecycle gating (required, per the lsp-kotlin maintainer): we drive [LifecycleState]
 * by hand — [initialized]/[shutdown]/[exit] advance it, and the forwarder
 * [awaitInitialized][LifecycleState.awaitInitialized]s before pushing the first
 * diagnostic so we never emit before the client sent `initialized`. We make NO blocking
 * client-bound request from inside [initialize] (deadlock risk).
 *
 * If the subprocess is unavailable (binary unset/missing or spawn failure),
 * [delegate] is null and the bridge degrades to an inert server: [initialize] returns
 * empty capabilities, notifications are dropped. The frontend is flag-gated anyway, so
 * with the flag off this class is never constructed.
 */
class BridgeLanguageServer(
    private val config: Config,
    private val workspaceId: String,
    private val konstructionId: String,
    private val frontendClient: KsrpcLanguageClient
) : DefaultLanguageServer() {

    private val hauler = hauler("BridgeLanguageServer")
    private val lifecycle = LifecycleState()
    private val lspWorkspace = KonstructionLspWorkspace(config, workspaceId, konstructionId)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The subprocess-facing stub; null when the engine is unavailable. */
    private var delegate: KsrpcLanguageServer? = null

    /**
     * The event-driven pull→push publisher (#43). Non-null ONLY when the subprocess
     * advertised pull-mode ([ServerCapabilities.diagnosticProvider] != null); otherwise we
     * run no pull machinery at all. Created in [initialize] once we have the engine's
     * capabilities.
     */
    private var diagnostics: PullDiagnosticsPublisher? = null

    /**
     * The frontend's document URI (`...content.csgs`), captured on the editor's first
     * didOpen. Diagnostics from the subprocess (on the wrapped-`.kt` URI) are rewritten
     * to this URI so kodemirror routes them to the open editor, and their ranges are
     * translated to csgs space (Phase 3 / #38).
     */
    @Volatile
    private var frontendUri: String? = null

    /**
     * Backend→subprocess client. Forwards diagnostics up to the frontend (gated on
     * `initialized`), and answers the engine's lifecycle-time requests with benign
     * defaults so it never blocks (matching the PoC: `workspace/configuration` → nulls,
     * (un)registerCapability → null).
     */
    private inner class Forwarder : DefaultLanguageClient() {
        override suspend fun textDocumentPublishDiagnostics(params: PublishDiagnosticsParams) {
            // Don't push before the editor has sent `initialized` (spec ordering; some
            // clients silently drop early diagnostics).
            lifecycle.awaitInitialized()
            // Rewrite the wrapped-.kt URI back to the editor's csgs URI AND translate every
            // range from wrapped-.kt space down to csgs space (Phase 3 / #38).
            val mapped = params.copy(
                uri = frontendUri ?: params.uri,
                diagnostics = translateDiagnostics(params.diagnostics)
            )
            runCatching { frontendClient.textDocumentPublishDiagnostics(mapped) }
                .onFailure { hauler.error("Failed forwarding diagnostics to editor", it) }
        }

        override suspend fun workspaceConfiguration(params: ConfigurationParams): List<LSPAny> =
            params.items.map { JsonNull }

        override suspend fun clientRegisterCapability(params: RegistrationParams): Nothing? = null

        override suspend fun clientUnregisterCapability(params: UnregistrationParams): Nothing? =
            null

        /**
         * ⚠️ The engine's `workspace/diagnostic/refresh` ("results may have changed,
         * re-pull"). The [DefaultLanguageClient] base THROWS `NotImplementedError` here
         * (the lsp-kotlin maintainer's flagged trap — distinct from the frontend client's
         * harmless no-op). Override it to re-pull every open doc via the publisher: this is
         * the cold-index-warmed case and the steady-state event that makes diagnostics
         * event-driven instead of a poll loop.
         */
        override suspend fun workspaceDiagnosticRefresh(): Nothing? {
            diagnostics?.onRefresh()
            return null
        }
    }

    override suspend fun initialize(params: InitializeParams): InitializeResult {
        // Synthesize the per-konstruction workspace (wrapped .kt + workspace.json) and
        // spawn/connect the warm subprocess. NO blocking client-bound request here.
        lspWorkspace.synthesize()
        val server = KotlinLspProcess.forConfig(config).connect(Forwarder())
        delegate = server
        if (server == null) {
            hauler.error("kotlin-lsp engine unavailable; bridge is inert")
            return InitializeResult(capabilities = com.monkopedia.lsp.ServerCapabilities())
        }
        // Drive the subprocess through the same handshake, but rooted at the synthesized
        // workspace so it loads our workspace.json + classpath. The editor's LSPClient
        // advertises EMPTY client capabilities; newer kotlin-lsp builds (LS-262.6274.0+)
        // gate features on the client declaring support — with no `textDocument.completion`
        // the engine returns no completions, and with no `textDocument.diagnostic` it omits
        // the pull `diagnosticProvider` entirely (so [PullDiagnosticsPublisher] never runs).
        // The bridge DOES support both (it forwards completion and runs the pull→push loop),
        // so we advertise them on the client's behalf before forwarding.
        val result = guardEngine(
            "initialize",
            InitializeResult(capabilities = ServerCapabilities())
        ) {
            server.initialize(
                params.copy(
                    rootUri = lspWorkspace.rootUri,
                    workspaceFolders = null,
                    capabilities = params.capabilities.withBridgeFeatures()
                )
            )
        }
        // Detect pull-mode authoritatively (#43): a non-null diagnosticProvider in the
        // engine's capabilities is the only signal (there is no push capability flag). Only
        // then do we stand up the event-driven pull→push publisher; otherwise no pull
        // machinery runs at all.
        diagnostics = PullDiagnosticsPublisher.pullProviderOf(result.capabilities)
            ?.let { provider -> createPublisher(server, provider) }
        return result
    }

    /**
     * Build the [PullDiagnosticsPublisher] wired to THIS bridge's subprocess [server] and
     * frontend client. The publisher owns the pull/refresh/previousResultId/debounce/backoff
     * logic; the bridge supplies only the three seams:
     *  - the PULL (subprocess `textDocument/diagnostic`, threading `previousResultId`),
     *  - the PUBLISH (translate ranges to csgs space + rewrite the URI, then push up), and
     *  - the readiness gate (the editor's `initialized` handshake).
     */
    private fun createPublisher(
        server: KsrpcLanguageServer,
        provider: com.monkopedia.lsp.ServerCapabilitiesDiagnosticProvider
    ): PullDiagnosticsPublisher = PullDiagnosticsPublisher(
        scope = scope,
        diagnosticProvider = provider,
        pull = { _, previousResultId ->
            server.textDocumentDiagnostic(
                DocumentDiagnosticParams(
                    textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri),
                    previousResultId = previousResultId
                )
            )
        },
        publish = { _, items ->
            // Translate wrapped-.kt ranges down to csgs space, rewrite to the editor's URI.
            val uri = frontendUri ?: lspWorkspace.documentUri
            frontendClient.textDocumentPublishDiagnostics(
                PublishDiagnosticsParams(
                    uri = uri,
                    diagnostics = translateDiagnostics(items)
                )
            )
        },
        awaitReady = { lifecycle.awaitInitialized() }
    )

    override suspend fun initialized(params: InitializedParams) {
        delegate?.let { runCatching { it.initialized(params) } }
        // Now the editor has initialized; release the forwarder so it can push.
        lifecycle.advanceTo(LifecycleState.Phase.INITIALIZED)
    }

    override suspend fun textDocumentDidOpen(params: DidOpenTextDocumentParams) {
        val server = delegate ?: return
        // Remember the editor's URI so subprocess diagnostics get routed back to it.
        frontendUri = params.textDocument.uri
        // Wrap the editor's LIVE csgs content (not just the stored snapshot) into the .kt
        // form and open the WRAPPED file in the engine, so the engine analyzes what the
        // user actually has open.
        lspWorkspace.synthesize(content = params.textDocument.text)
        guardEngine("didOpen", Unit) {
            server.textDocumentDidOpen(
                DidOpenTextDocumentParams(
                    textDocument = TextDocumentItem(
                        uri = lspWorkspace.documentUri,
                        languageId = "kotlin",
                        version = params.textDocument.version,
                        text = lspWorkspace.wrappedText()
                    )
                )
            )
        }
        // Event-driven (#43): pull now (with cold-index backoff for this first pull only),
        // then rely on didChange/refresh. Routes by the frontend's csgs URI.
        diagnostics?.onOpen(params.textDocument.uri)
    }

    override suspend fun textDocumentDidChange(params: DidChangeTextDocumentParams) {
        val server = delegate ?: return
        // Full-document sync (v1): the editor sends the whole edited csgs as the change
        // text (kodemirror full-sync; these files are tiny). Take the last change's text
        // as the live document, re-wrap it into the .kt form so the engine analyzes the
        // current content, and forward the WRAPPED text down as a full-document change.
        val csgsText = params.contentChanges
            .filterIsInstance<TextDocumentContentChangeEventVariant>()
            .lastOrNull()?.text ?: return
        lspWorkspace.synthesize(content = csgsText)
        runCatching {
            server.textDocumentDidChange(
                DidChangeTextDocumentParams(
                    textDocument = VersionedTextDocumentIdentifier(
                        uri = lspWorkspace.documentUri,
                        version = params.textDocument.version
                    ),
                    contentChanges = listOf(
                        TextDocumentContentChangeEventVariant(text = lspWorkspace.wrappedText())
                    )
                )
            )
        }.onFailure { hauler.error("Failed forwarding didChange to engine", it) }
        // Event-driven (#43): debounced re-pull for the new content (coalesces edit bursts).
        diagnostics?.onChange(params.textDocument.uri)
    }

    override suspend fun textDocumentDidClose(params: DidCloseTextDocumentParams) {
        val server = delegate ?: return
        // Key the publisher's close by the SAME frontend csgs uri that onOpen/onChange used
        // (`params.textDocument.uri`), not the stashed [frontendUri], so the doc is guaranteed
        // to leave openDocs/resultIds (uri-space key consistency — a mismatched key would leak
        // the doc and keep re-pulling it on every refresh after close). The publisher's own
        // publish seam translates to the wrapped uri at the boundary.
        diagnostics?.onClose(params.textDocument.uri)
        runCatching {
            server.textDocumentDidClose(
                DidCloseTextDocumentParams(
                    textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri)
                )
            )
        }
    }

    /**
     * Run a subprocess-facing ([delegate]) call, degrading to [fallback] if it fails.
     *
     * Failure isolation (observed live 2026-06-10): the kotlin-lsp subprocess can crash
     * mid-session. A throwing delegate call must NOT propagate an exception out of an LSP
     * method, because that exception rides up the **shared** frontend ksrpc connection and
     * tears the whole connection down — after which unrelated [KonstructionService] calls
     * (`getInfo`, `requestKonstructs`) all fail with "MultiChannel is closed" and the editor
     * silently stops compiling/executing. So every forward call degrades to inert here: the
     * worst a dead engine can do is "no completion / no hover", never kill the session.
     * Cooperative cancellation is re-thrown; everything else returns [fallback].
     */
    private suspend fun <T> guardEngine(label: String, fallback: T, block: suspend () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            // Propagate ONLY a genuine cancellation of *this* coroutine (cooperative cancellation).
            // A FOREIGN CancellationException — the dead kotlin-lsp subprocess leg's MultiChannel
            // close surfacing through a delegate call — must NOT propagate: ksrpc converts thrown
            // errors to error frames EXCEPT CancellationException, which it rethrows, so re-throwing
            // it here escapes the call handler, cancels the frontend connection's serviceScope, and
            // closes its MultiChannel — taking the whole editor session down (ksrpc#228, candidate B).
            // ensureActive() rethrows only if WE are actually cancelled; otherwise the foreign
            // cancellation is treated like any other engine failure and the bridge degrades to inert.
            coroutineContext.ensureActive()
            hauler.error("kotlin-lsp '$label' failed; bridge degraded to inert for this call", t)
            fallback
        }

    /**
     * Completion (Phase 4 / #39). The editor sends a cursor [Position] in **csgs** space;
     * translate it UP to wrapped-`.kt` space (`ktLine = csgsLine + headerLines`) and
     * retarget the document at the wrapped file before delegating. Completion items can
     * carry a `textEdit`/`insertReplaceEdit` whose range is in wrapped-`.kt` space; on the
     * way back, translate every such range DOWN to csgs space (the same Phase 3 mapping the
     * diagnostics use) so the editor applies the edit on the right line.
     */
    override suspend fun textDocumentCompletion(
        params: CompletionParams
    ): TextDocumentCompletionResult? {
        val server = delegate ?: return null
        // lsp-types 1.2.0 types this spec-nullable result honestly: a server returning `null`
        // (no completions / index not ready) decodes to `null`. A crashed engine, though, would
        // THROW — and an exception escaping here rides up the shared frontend connection and
        // closes it. Guard so a dead engine just yields "no popup" (see [guardEngine]).
        return guardEngine("completion", null) {
            val result = server.textDocumentCompletion(
                params.copy(
                    textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri),
                    position = DiagnosticTranslation.toWrappedPosition(params.position)
                )
            ) ?: return@guardEngine null
            translateCompletionResult(result)
        }
    }

    /**
     * Resolve (lazy completion detail). The resolved item may now carry a `textEdit` range
     * (it is often omitted from the initial list and filled in on resolve); translate it
     * back to csgs space too.
     */
    override suspend fun completionItemResolve(params: CompletionItem): CompletionItem {
        val server = delegate ?: return params
        return guardEngine("completionItemResolve", params) {
            translateCompletionItem(server.completionItemResolve(params))
        }
    }

    /**
     * Hover (Phase 4 / #39). Translate the request cursor UP to wrapped space; translate the
     * response's optional [Hover.range] back DOWN to csgs space so the highlight lands on
     * the user's line. [Hover.contents] is opaque markup with no positions, so it passes
     * through untouched.
     */
    override suspend fun textDocumentHover(params: HoverParams): Hover? {
        val server = delegate ?: return null
        // Hover is `Hover | null` per spec: kotlin-lsp returns null for hover-over-nothing, which
        // the editor fires on every cursor move. lsp-types 1.2.0 types it nullable, so null now
        // decodes to null (no exception, no scope cancellation). Return null → editor shows no
        // tooltip; only translate the range when there IS a hover.
        return guardEngine("hover", null) {
            val hover = server.textDocumentHover(
                params.copy(
                    textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri),
                    position = DiagnosticTranslation.toWrappedPosition(params.position)
                )
            ) ?: return@guardEngine null
            // When the cursor sits over whitespace inside the user's content, the engine resolves
            // the hover to the ENCLOSING wrapper construct — the `KcsgScript().apply { ... }` call
            // whose lambda body holds the csgs. Its range lands on a header line, so translateRange
            // drops to null. A hover that carries a range we can't map into csgs space is a wrapping
            // artifact the user can neither see nor target, so SUPPRESS the whole hover (return null)
            // rather than show the wrapper's tooltip with no highlight — the same header/footer drop
            // the diagnostics do.
            val engineRange = hover.range
            if (engineRange != null) {
                val csgsRange =
                    DiagnosticTranslation.translateRange(engineRange, lspWorkspace.csgsLineCount())
                        ?: return@guardEngine null
                return@guardEngine hover.copy(range = csgsRange)
            }
            // No range at all: a positionless hover (rare) — pass it through unchanged.
            hover
        }
    }

    /**
     * SignatureHelp (Phase 4 / #39). Translate the request cursor UP to wrapped space. The
     * response ([SignatureHelp]: signatures, parameter offsets, active indices) carries no
     * document positions, so it passes through unchanged.
     */
    override suspend fun textDocumentSignatureHelp(params: SignatureHelpParams): SignatureHelp? {
        val server = delegate ?: return null
        // `SignatureHelp | null` per spec (null when the cursor isn't in a call). lsp-types 1.2.0
        // types it nullable, so null propagates cleanly instead of throwing; a crashed engine is
        // guarded to inert so it can't take the shared connection down.
        return guardEngine("signatureHelp", null) {
            server.textDocumentSignatureHelp(
                params.copy(
                    textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri),
                    position = DiagnosticTranslation.toWrappedPosition(params.position)
                )
            )
        }
    }

    /**
     * The editor's `shutdown` — END OF THIS KONSTRUCTION'S SESSION, NOT of the engine. The
     * kotlin-lsp subprocess + its stdio connection are SHARED across konstructions and owned
     * by [KotlinLspProcess] (keep-warm). Forwarding `shutdown`/`exit`/`close` to the shared
     * subprocess stub would shut the engine down (or close the shared channel) for EVERY open
     * konstruction and break the next reopen. So we do NOT forward it; we only advance our own
     * [LifecycleState] and let [teardown] do per-konstruction cleanup. The engine forgets our
     * document via the `didClose` we send (in [textDocumentDidClose] and, defensively, in
     * [teardown]).
     */
    override suspend fun shutdown(): Nothing? {
        lifecycle.advanceTo(LifecycleState.Phase.SHUTTING_DOWN)
        return null
    }

    override suspend fun exit() {
        lifecycle.advanceTo(LifecycleState.Phase.EXITED)
        teardown()
    }

    /**
     * Teardown / reverse-channel release (Phase 5 / #40). ksrpc invokes this when the editor
     * closes the returned `lsp()` server stub (the empty-endpoint close removes this
     * sub-service from the host `serviceMap` and calls [SuspendCloseable.close]). Our
     * WebSocket is long-lived + shared across konstructions, so without this BOTH the
     * `lsp()` sub-service AND the reverse [frontendClient] channel would stay registered
     * until the whole socket closes — leaking two sub-channels per open→close cycle.
     *
     * So on close we [teardown] (cancel the bridge scope, stop the publisher, exit+close the
     * subprocess-facing stub) AND additionally [close][KsrpcLanguageClient.close] the
     * stashed [frontendClient], which drops our reference to its reverse channel and lets
     * ksrpc reclaim it. Idempotent and ordered after [teardown] so the publisher (which
     * pushes to [frontendClient]) is already stopped before we release it.
     *
     * Note: the warm subprocess itself ([KotlinLspProcess]) intentionally stays alive across
     * konstructions (keep-warm); we only release this konstruction's stub + reverse channel.
     */
    override suspend fun close() {
        teardown()
        runCatching { frontendClient.close() }
            .onFailure { hauler.error("Failed releasing reverse LSP client channel", it) }
    }

    /**
     * Per-konstruction cleanup. Drops the publisher, sends a best-effort `didClose` so the
     * SHARED engine forgets this konstruction's document, then cancels the bridge scope and
     * waits for it to fully stop. Deliberately does NOT `shutdown`/`exit`/`close` the
     * subprocess-facing [delegate] stub, because that stub rides the shared keep-warm
     * connection — closing it would kill the engine for other open konstructions and break
     * the next reopen. Idempotent: a second pass is a no-op once [delegate] is nulled.
     */
    private suspend fun teardown() {
        diagnostics = null
        delegate?.let { server ->
            // Tell the shared engine to forget our document (no-op if didClose already ran).
            runCatching {
                server.textDocumentDidClose(
                    DidCloseTextDocumentParams(
                        textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri)
                    )
                )
            }
        }
        delegate = null
        // cancelAndJoin: wait for all scope children (hauler writers, etc.) to actually finish
        // before returning, so callers that free resources (e.g. the temp dir in tests) do not
        // race with in-flight IO coroutines.
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    /**
     * Translate engine diagnostics (wrapped-`.kt` space) down to csgs space, dropping any
     * that fall in the wrapping header/footer (a synthetic line the user can't fix). See
     * [DiagnosticTranslation]; the csgs line count is the live document last synthesized.
     */
    private fun translateDiagnostics(diagnostics: List<Diagnostic>): List<Diagnostic> =
        DiagnosticTranslation.translateDiagnostics(
            diagnostics.filterNot(::isMissingJdkFalsePositive),
            lspWorkspace.csgsLineCount()
        )

    /**
     * STOPGAP (tracked): the synthesized workspace.json has no working JDK on the LSP classpath
     * (the `jrt://` SDK roots aren't resolving against this engine's JSON-import path yet — a
     * golden-file format issue under investigation with the lsp-kotlin maintainer). Without a JDK
     * the engine flags every reference to a `java.*` supertype — e.g. `java.io.Serializable`, a
     * supertype of `kotlin.Double` — so EVERY numeric literal/coordinate in a kcsg script becomes
     * a false "Cannot access 'java.…' … Check your module classpath" error (100+ on a real model).
     * That flood both renders as noise AND lags the editor. Until the JDK SDK roots land, suppress
     * exactly this missing-classpath cascade. It is narrow: real user errors (unresolved kcsg refs,
     * type mismatches) carry different messages and still surface.
     */
    private fun isMissingJdkFalsePositive(d: Diagnostic): Boolean =
        d.message.contains("Cannot access 'java.") &&
            d.message.contains("Check your module classpath")

    /**
     * Translate every wrapped-`.kt` range carried by a completion result DOWN to csgs
     * space. The result is one of two ksrpc variants: a bare array of [CompletionItem] or a
     * [CompletionList]; both reach the items the same way. Items whose edit range maps into
     * the header/footer are kept but their edit is dropped (no synthetic-line edit ever
     * reaches the editor) — the editor falls back to plain-text insertion.
     */
    private fun translateCompletionResult(
        result: TextDocumentCompletionResult
    ): TextDocumentCompletionResult = when (result) {
        is TextDocumentCompletionResult.CompletionItemArray ->
            TextDocumentCompletionResult.CompletionItemArray(
                result.value.map { translateCompletionItem(it) }
            )

        is TextDocumentCompletionResult.CompletionListValue ->
            TextDocumentCompletionResult.CompletionListValue(
                result.value.copy(items = result.value.items.map { translateCompletionItem(it) })
            )
    }

    /**
     * Translate a single completion item's edit ranges from wrapped-`.kt` to csgs space:
     * the primary [CompletionItem.textEdit] (a `TextEdit` with a `range`, or an
     * `InsertReplaceEdit` with `insert`+`replace` ranges) and any [additionalTextEdits].
     * An edit that can't be mapped back (header/footer) is dropped rather than misplaced.
     */
    private fun translateCompletionItem(item: CompletionItem): CompletionItem {
        val csgsLineCount = lspWorkspace.csgsLineCount()
        val textEdit = item.textEdit?.let { translateCompletionEdit(it, csgsLineCount) }
        val additional = item.additionalTextEdits?.mapNotNull { edit ->
            DiagnosticTranslation.translateRange(edit.range, csgsLineCount)
                ?.let { edit.copy(range = it) }
        }
        return item.copy(textEdit = textEdit, additionalTextEdits = additional)
    }

    /**
     * Translate the union [CompletionItemTextEdit] (`TextEdit` | `InsertReplaceEdit`) back
     * to csgs space, returning null (drop the edit) when a range falls in the header/footer.
     */
    private fun translateCompletionEdit(
        edit: CompletionItemTextEdit,
        csgsLineCount: Int
    ): CompletionItemTextEdit? = when (edit) {
        is TextEdit ->
            DiagnosticTranslation.translateRange(edit.range, csgsLineCount)
                ?.let { edit.copy(range = it) }

        is InsertReplaceEdit -> {
            val insert = DiagnosticTranslation.translateRange(edit.insert, csgsLineCount)
            val replace = DiagnosticTranslation.translateRange(edit.replace, csgsLineCount)
            if (insert != null && replace != null) {
                edit.copy(insert = insert, replace = replace)
            } else {
                null
            }
        }
    }
}

/**
 * Advertise the `textDocument` features the bridge actually services — `completion` and
 * pull `diagnostic` — so spec-strict engine builds (LS-262.6274.0+) offer them even though
 * the editor's `LSPClient` initializes with empty [ClientCapabilities]. Any sub-capabilities
 * the editor DID send are preserved; we only fill the gaps. Hover/signature-help are left
 * untouched (they are plain request/response and already resolve without being advertised).
 */
private fun ClientCapabilities.withBridgeFeatures(): ClientCapabilities {
    val td = textDocument ?: TextDocumentClientCapabilities()
    return copy(
        textDocument = td.copy(
            completion = td.completion ?: CompletionClientCapabilities(),
            diagnostic = td.diagnostic ?: DiagnosticClientCapabilities()
        )
    )
}
