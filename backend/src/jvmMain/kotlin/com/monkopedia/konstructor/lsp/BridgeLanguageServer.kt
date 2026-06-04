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
import com.monkopedia.lsp.CompletionParams
import com.monkopedia.lsp.ConfigurationParams
import com.monkopedia.lsp.DefaultLanguageClient
import com.monkopedia.lsp.DefaultLanguageServer
import com.monkopedia.lsp.Diagnostic
import com.monkopedia.lsp.DidChangeTextDocumentParams
import com.monkopedia.lsp.DidCloseTextDocumentParams
import com.monkopedia.lsp.DidOpenTextDocumentParams
import com.monkopedia.lsp.DocumentDiagnosticParams
import com.monkopedia.lsp.DocumentDiagnosticReport
import com.monkopedia.lsp.Hover
import com.monkopedia.lsp.HoverParams
import com.monkopedia.lsp.InitializeParams
import com.monkopedia.lsp.InitializeResult
import com.monkopedia.lsp.InitializedParams
import com.monkopedia.lsp.KsrpcLanguageClient
import com.monkopedia.lsp.KsrpcLanguageServer
import com.monkopedia.lsp.LSPAny
import com.monkopedia.lsp.PublishDiagnosticsParams
import com.monkopedia.lsp.RegistrationParams
import com.monkopedia.lsp.RelatedFullDocumentDiagnosticReport
import com.monkopedia.lsp.SignatureHelp
import com.monkopedia.lsp.SignatureHelpParams
import com.monkopedia.lsp.TextDocumentCompletionResult
import com.monkopedia.lsp.TextDocumentContentChangeEventVariant
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextDocumentItem
import com.monkopedia.lsp.UnregistrationParams
import com.monkopedia.lsp.VersionedTextDocumentIdentifier
import com.monkopedia.lsp.ksrpc.LifecycleState
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 *   [DiagnosticTranslation]; completion/hover positions stay in wrapped space (Phase 4 /
 *   #39).
 *
 * **Pull→push diagnostics bridge.** kotlin-lsp does NOT proactively push
 * `publishDiagnostics`; it answers PULL `textDocument/diagnostic`. kodemirror's editor
 * client, however, listens for PUSHED diagnostics (the Phase 1 design). So on
 * didOpen/didChange the bridge polls the engine via pull and forwards the resulting
 * items up to the editor as `publishDiagnostics` (with retry, since the cold index can
 * take ~120s to settle). If kotlin-lsp ever also pushes, the [Forwarder] forwards those
 * too — they coexist.
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

    /** The in-flight diagnostics poll, cancelled/replaced on each didOpen. */
    private var diagnosticsPoll: Job? = null

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
        // workspace so it loads our workspace.json + classpath.
        return server.initialize(
            params.copy(
                rootUri = lspWorkspace.rootUri,
                workspaceFolders = null
            )
        )
    }

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
        startDiagnosticsPoll(server)
    }

    /**
     * Poll the engine via pull `textDocument/diagnostic` and push each result up to the
     * editor as `publishDiagnostics`. Bridges kotlin-lsp's pull-only diagnostics to the
     * editor client's push expectation. Retries (the cold index can take ~120s to
     * settle), then keeps refreshing at a slower cadence so later edits/analysis updates
     * still reach the editor. Gated on `initialized` before the first push.
     */
    private fun startDiagnosticsPoll(server: KsrpcLanguageServer) {
        diagnosticsPoll?.cancel()
        diagnosticsPoll = scope.launch {
            lifecycle.awaitInitialized()
            var attempt = 0
            while (true) {
                val items = runCatching { pullDiagnostics(server) }
                    .onFailure { hauler.error("pull diagnostics failed", it) }
                    .getOrNull()
                if (items != null) {
                    val uri = frontendUri ?: lspWorkspace.documentUri
                    // Translate wrapped-.kt ranges down to csgs space before pushing up.
                    val translated = translateDiagnostics(items)
                    runCatching {
                        frontendClient.textDocumentPublishDiagnostics(
                            PublishDiagnosticsParams(uri = uri, diagnostics = translated)
                        )
                    }.onFailure { hauler.error("publish diagnostics to editor failed", it) }
                }
                // Faster while warming up, then a slow steady-state refresh.
                attempt++
                delay(if (attempt < WARMUP_ATTEMPTS) WARMUP_INTERVAL else STEADY_INTERVAL)
            }
        }
    }

    private suspend fun pullDiagnostics(server: KsrpcLanguageServer): List<Diagnostic> {
        val report: DocumentDiagnosticReport = server.textDocumentDiagnostic(
            DocumentDiagnosticParams(
                textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri)
            )
        )
        // Only a FULL report carries items; an UNCHANGED report means "no new items".
        return (report as? RelatedFullDocumentDiagnosticReport)?.items ?: emptyList()
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
        // Refresh diagnostics for the new content (re-poll; same path as didOpen).
        startDiagnosticsPoll(server)
    }

    override suspend fun textDocumentDidClose(params: DidCloseTextDocumentParams) {
        val server = delegate ?: return
        runCatching {
            server.textDocumentDidClose(
                DidCloseTextDocumentParams(
                    textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri)
                )
            )
        }
    }

    override suspend fun textDocumentCompletion(
        params: CompletionParams
    ): TextDocumentCompletionResult {
        val server = delegate ?: return super.textDocumentCompletion(params)
        return server.textDocumentCompletion(params.onWrappedDocument())
    }

    override suspend fun textDocumentHover(params: HoverParams): Hover {
        val server = delegate ?: return super.textDocumentHover(params)
        return server.textDocumentHover(
            params.copy(textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri))
        )
    }

    override suspend fun textDocumentSignatureHelp(params: SignatureHelpParams): SignatureHelp {
        val server = delegate ?: return super.textDocumentSignatureHelp(params)
        return server.textDocumentSignatureHelp(
            params.copy(textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri))
        )
    }

    override suspend fun shutdown(): Nothing? {
        lifecycle.advanceTo(LifecycleState.Phase.SHUTTING_DOWN)
        return delegate?.let { runCatching { it.shutdown() }.getOrNull() }
    }

    override suspend fun exit() {
        lifecycle.advanceTo(LifecycleState.Phase.EXITED)
        diagnosticsPoll?.cancel()
        scope.coroutineContext[Job]?.cancel()
        delegate?.let { server ->
            runCatching { server.exit() }
            // Close the subprocess-facing stub so its reverse channel is released (cheap
            // leak guard; full teardown/leak test is Phase 5 / #40).
            runCatching { server.close() }
        }
        delegate = null
    }

    /**
     * Translate engine diagnostics (wrapped-`.kt` space) down to csgs space, dropping any
     * that fall in the wrapping header/footer (a synthetic line the user can't fix). See
     * [DiagnosticTranslation]; the csgs line count is the live document last synthesized.
     */
    private fun translateDiagnostics(diagnostics: List<Diagnostic>): List<Diagnostic> =
        DiagnosticTranslation.translateDiagnostics(diagnostics, lspWorkspace.csgsLineCount())

    private fun CompletionParams.onWrappedDocument(): CompletionParams =
        copy(textDocument = TextDocumentIdentifier(uri = lspWorkspace.documentUri))

    private companion object {
        private const val WARMUP_ATTEMPTS = 30
        private val WARMUP_INTERVAL = 4.seconds
        private val STEADY_INTERVAL = 15.seconds
    }
}
