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
package com.monkopedia.konstructor.integration

import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.lsp.BridgeLanguageServer
import com.monkopedia.konstructor.testutil.TestEnvironment
import com.monkopedia.lsp.ClientCapabilities
import com.monkopedia.lsp.CompletionParams
import com.monkopedia.lsp.DefaultLanguageClient
import com.monkopedia.lsp.DidCloseTextDocumentParams
import com.monkopedia.lsp.DidOpenTextDocumentParams
import com.monkopedia.lsp.HoverParams
import com.monkopedia.lsp.InitializeParams
import com.monkopedia.lsp.InitializedParams
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.PublishDiagnosticsParams
import com.monkopedia.lsp.TextDocumentCompletionResult
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextDocumentItem
import java.io.File
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * REAL-engine bridge verification (epic #35 Phase 2 / #37). Drives the actual
 * [BridgeLanguageServer] against a live JetBrains `kotlin-lsp` subprocess and asserts a
 * genuine kcsg-aware diagnostic flows through the forwarder to the (frontend-side)
 * client.
 *
 * **Not CI-tested**: CI has neither the 393MB binary nor the bundled lib resource. This
 * is double-gated and skips cleanly unless BOTH:
 *  - `-Dintegration=true` (full build present, so `lib.jar` extracts), AND
 *  - `KONSTRUCTOR_KOTLIN_LSP` points at a real `intellij-server` launcher.
 *
 * Local invocation:
 * ```
 * JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
 *   KONSTRUCTOR_KOTLIN_LSP=/tmp/kotlin-lsp-poc/server/kotlin-server-262.4739.0/bin/intellij-server \
 *   ./gradlew shadowJar :backend:jvmTest -Dintegration=true \
 *     --tests '*BridgeLanguageServerIntegrationTest*'
 * ```
 */
class BridgeLanguageServerIntegrationTest {

    private var env: TestEnvironment? = null

    @Before
    fun setUp() {
        assumeTrue(
            "Set -Dintegration=true to run bridge integration tests",
            System.getProperty("integration") == "true"
        )
        val binary = System.getenv("KONSTRUCTOR_KOTLIN_LSP")
        assumeTrue(
            "Set KONSTRUCTOR_KOTLIN_LSP to a real intellij-server to run this test",
            binary != null && File(binary).canExecute()
        )
        env = TestEnvironment()
    }

    @After
    fun tearDown() {
        env?.close()
    }

    /**
     * A deliberately broken csgs: `cube` is referenced with an UNRESOLVED symbol, which
     * the kcsg-aware engine must flag — while the valid `cube { ... }` DSL on the line
     * above must NOT produce a false positive.
     */
    private val deliberateErrorScript = """
        val ok by primitive { cube { dimensions = xyz(1.0, 1.0, 1.0) } }
        val broken = thisSymbolDoesNotExist123
        export("ok")
    """.trimIndent()

    @Test
    fun realEngineProducesKcsgDiagnostic() = runBlocking {
        val config = env!!.config
        val workspaceId = "0"
        val konstructionId = "0"
        // Seed the konstruction content the bridge will wrap + open.
        val paths: PathController.Paths = PathController(config)[workspaceId, konstructionId]
        paths.contentFile.writeText(deliberateErrorScript)

        val frontendUri = "file:///$workspaceId/$konstructionId/content.csgs"
        val diagnostics = CompletableDeferred<PublishDiagnosticsParams>()
        val frontendClient = object : DefaultLanguageClient() {
            override suspend fun textDocumentPublishDiagnostics(params: PublishDiagnosticsParams) {
                if (params.diagnostics.isNotEmpty() && !diagnostics.isCompleted) {
                    diagnostics.complete(params)
                }
            }
        }

        val bridge = BridgeLanguageServer(config, workspaceId, konstructionId, frontendClient)

        // initialize -> initialized -> didOpen, exactly as the editor's LSPClient drives.
        val init = bridge.initialize(
            InitializeParams(
                capabilities = ClientCapabilities(),
                processId = ProcessHandle.current().pid().toInt(),
                rootUri = "file:///$workspaceId"
            )
        )
        assertTrue(init.capabilities != null, "engine returned no capabilities")

        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(
            DidOpenTextDocumentParams(
                textDocument = TextDocumentItem(
                    uri = frontendUri,
                    languageId = "kotlin",
                    version = 1,
                    text = deliberateErrorScript
                )
            )
        )

        // Warm/cold index can take ~120s cold; poll generously.
        val received = withTimeoutOrNull(180_000) {
            while (!diagnostics.isCompleted) delay(500)
            diagnostics.await()
        }

        bridge.shutdown()
        bridge.exit()

        assertTrue(received != null, "no diagnostics flowed from the engine within budget")
        // Forwarder rewrites the wrapped-.kt URI back to the editor's csgs URI.
        assertTrue(
            received.uri == frontendUri,
            "diagnostics must be routed to the editor URI; got ${received.uri}"
        )
        // The unresolved reference must be flagged (real kcsg-aware analysis).
        val messages = received.diagnostics.joinToString("; ") { it.message }
        assertTrue(
            received.diagnostics.isNotEmpty(),
            "expected at least one diagnostic for the deliberate error; got none"
        )
        System.err.println("REAL-ENGINE diagnostics (${received.diagnostics.size}): $messages")
        val unresolved = received.diagnostics.firstOrNull {
            it.message.contains("thisSymbolDoesNotExist123") ||
                it.message.contains("unresolved", ignoreCase = true) ||
                it.message.contains("cannot access", ignoreCase = true)
        }
        assertTrue(
            unresolved != null,
            "expected the unresolved-reference error to be reported; got: $messages"
        )
        // Phase 3 (#38): the deliberate error is on csgs line index 1 (the `val broken`
        // line, 0-based). The engine reports it on wrapped line `1 + headerLines`; after
        // the translation it MUST land back on csgs line 1 (not N+headerLines). This is
        // the real-engine half of the round-trip guard.
        System.err.println(
            "REAL-ENGINE unresolved-ref range: ${unresolved.range.start.line.toInt()}.." +
                "${unresolved.range.end.line.toInt()}"
        )
        assertTrue(
            unresolved.range.start.line.toInt() == EXPECTED_CSGS_ERROR_LINE,
            "translated diagnostic must land on csgs line $EXPECTED_CSGS_ERROR_LINE " +
                "(the `val broken` line), not the wrapped-.kt line; " +
                "got line ${unresolved.range.start.line.toInt()}"
        )
    }

    /**
     * Teardown + reopen (epic #35 Phase 5 / #40), real engine. Drives the full editor
     * lifecycle TWICE on one konstruction with a real shutdown→exit→close teardown in
     * between, asserting:
     *  1. diagnostics flow after the FIRST open,
     *  2. on close the bridge clears (an empty publish for the doc — squiggles don't linger),
     *  3. after a fresh REOPEN diagnostics flow AGAIN (the warm subprocess survived the
     *     teardown; keep-warm intact), and
     *  4. no STALE diagnostics from the closed doc reappear on reopen (the close cleared
     *     state; the publish-after-close guard held).
     *
     * Local-only (same double gate as the other real-engine tests). This is the real-engine
     * half of the leak/teardown guard; the CI-runnable half is [LspSubserviceLeakTest] +
     * [PullDiagnosticsPublisherTest].
     */
    @Test
    fun realEngineOpenCloseReopenStillFlowsNoStale() = runBlocking {
        val config = env!!.config
        val workspaceId = "0"
        val konstructionId = "0"
        val paths: PathController.Paths = PathController(config)[workspaceId, konstructionId]
        paths.contentFile.writeText(deliberateErrorScript)
        val frontendUri = "file:///$workspaceId/$konstructionId/content.csgs"

        // Records every publish (uri + count) so we can assert the clear-on-close and the
        // diagnostics-after-reopen behaviours.
        val publishes = java.util.Collections.synchronizedList(
            mutableListOf<PublishDiagnosticsParams>()
        )
        fun makeClient(firstNonEmpty: CompletableDeferred<Unit>) = object :
            DefaultLanguageClient() {
            override suspend fun textDocumentPublishDiagnostics(params: PublishDiagnosticsParams) {
                publishes.add(params)
                if (params.diagnostics.isNotEmpty() && !firstNonEmpty.isCompleted) {
                    firstNonEmpty.complete(Unit)
                }
            }
        }

        // --- FIRST open ---
        val firstDiag = CompletableDeferred<Unit>()
        val bridge1 = BridgeLanguageServer(
            config,
            workspaceId,
            konstructionId,
            makeClient(firstDiag)
        )
        bridge1.initialize(
            InitializeParams(
                capabilities = ClientCapabilities(),
                processId = ProcessHandle.current().pid().toInt(),
                rootUri = "file:///$workspaceId"
            )
        )
        bridge1.initialized(InitializedParams())
        bridge1.textDocumentDidOpen(
            DidOpenTextDocumentParams(
                textDocument = TextDocumentItem(
                    uri = frontendUri,
                    languageId = "kotlin",
                    version = 1,
                    text = deliberateErrorScript
                )
            )
        )
        val firstFlowed = withTimeoutOrNull(180_000) {
            firstDiag.await()
            true
        }
        assertTrue(firstFlowed == true, "no diagnostics flowed on the first open within budget")

        // --- CLOSE + full teardown (shutdown→exit→close) ---
        bridge1.textDocumentDidClose(
            DidCloseTextDocumentParams(
                textDocument = TextDocumentIdentifier(uri = frontendUri)
            )
        )
        // Let the clear-on-close publish land.
        withTimeoutOrNull(10_000) {
            while (publishes.none { it.uri == frontendUri && it.diagnostics.isEmpty() }) {
                delay(100)
            }
        }
        bridge1.shutdown()
        bridge1.exit()
        bridge1.close()

        val sawClear = publishes.any { it.uri == frontendUri && it.diagnostics.isEmpty() }
        assertTrue(sawClear, "close must emit a clearing (empty) publish so squiggles don't linger")

        // Snapshot the publish count after teardown; nothing more should land for the closed
        // doc (the publish-after-close guard + the cleared state).
        delay(3_000)
        val afterTeardownCount = publishes.size

        // --- REOPEN (fresh bridge, same warm subprocess) ---
        publishes.clear()
        val secondDiag = CompletableDeferred<Unit>()
        val bridge2 = BridgeLanguageServer(
            config,
            workspaceId,
            konstructionId,
            makeClient(secondDiag)
        )
        bridge2.initialize(
            InitializeParams(
                capabilities = ClientCapabilities(),
                processId = ProcessHandle.current().pid().toInt(),
                rootUri = "file:///$workspaceId"
            )
        )
        bridge2.initialized(InitializedParams())
        bridge2.textDocumentDidOpen(
            DidOpenTextDocumentParams(
                textDocument = TextDocumentItem(
                    uri = frontendUri,
                    languageId = "kotlin",
                    version = 1,
                    text = deliberateErrorScript
                )
            )
        )
        val secondFlowed = withTimeoutOrNull(180_000) {
            secondDiag.await()
            true
        }

        bridge2.shutdown()
        bridge2.exit()
        bridge2.close()

        System.err.println(
            "REAL-ENGINE reopen: firstFlowed=$firstFlowed sawClear=$sawClear " +
                "afterTeardownCount=$afterTeardownCount secondFlowed=$secondFlowed"
        )
        assertTrue(
            secondFlowed == true,
            "diagnostics must flow again after a reopen (warm subprocess survived teardown)"
        )
        // The reopen's diagnostics must be a FRESH non-empty publish for the doc, not a
        // leftover/stale one — proven by secondDiag completing only on a non-empty publish
        // AFTER the publishes list was cleared post-teardown.
        assertTrue(
            publishes.any { it.uri == frontendUri && it.diagnostics.isNotEmpty() },
            "the reopen must produce fresh diagnostics for the doc"
        )
    }

    /**
     * Event-driven path (#43), real engine: after the FIRST diagnostic publish arrives via
     * the didOpen pull, the bridge must NOT keep re-publishing on a timer (the old blind
     * poll did, every 15s). With `previousResultId` + the `unchanged`-report skip, a
     * settled doc stops emitting redundant pushes — so once diagnostics arrive, the publish
     * count must stabilise over a further window. This is the real-engine half of the
     * `unchanged`-dedup guard (the CI-runnable half is in [PullDiagnosticsPublisherTest]).
     */
    @Test
    fun realEngineEventDrivenPullDoesNotDoublePublish() = runBlocking {
        val config = env!!.config
        val workspaceId = "0"
        val konstructionId = "0"
        val paths: PathController.Paths = PathController(config)[workspaceId, konstructionId]
        paths.contentFile.writeText(deliberateErrorScript)
        val frontendUri = "file:///$workspaceId/$konstructionId/content.csgs"

        val firstNonEmpty = CompletableDeferred<Unit>()
        val publishCount = java.util.concurrent.atomic.AtomicInteger(0)
        val frontendClient = object : DefaultLanguageClient() {
            override suspend fun textDocumentPublishDiagnostics(params: PublishDiagnosticsParams) {
                publishCount.incrementAndGet()
                if (params.diagnostics.isNotEmpty() && !firstNonEmpty.isCompleted) {
                    firstNonEmpty.complete(Unit)
                }
            }
        }

        val bridge = BridgeLanguageServer(config, workspaceId, konstructionId, frontendClient)
        bridge.initialize(
            InitializeParams(
                capabilities = ClientCapabilities(),
                processId = ProcessHandle.current().pid().toInt(),
                rootUri = "file:///$workspaceId"
            )
        )
        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(
            DidOpenTextDocumentParams(
                textDocument = TextDocumentItem(
                    uri = frontendUri,
                    languageId = "kotlin",
                    version = 1,
                    text = deliberateErrorScript
                )
            )
        )

        val settled = withTimeoutOrNull(180_000) {
            firstNonEmpty.await()
            true
        }
        assertTrue(settled == true, "no diagnostics flowed from the engine within budget")

        // Snapshot the count, then let the system sit idle: with the event-driven model the
        // settled doc must not keep republishing on a timer. Allow at most a small grace
        // (an in-flight backoff pull or a single engine refresh), but NOT a steady stream.
        val afterSettle = publishCount.get()
        delay(20_000)
        val afterIdle = publishCount.get()

        bridge.shutdown()
        bridge.exit()

        System.err.println(
            "REAL-ENGINE publishes after settle=$afterSettle, after 20s idle=$afterIdle"
        )
        assertTrue(
            afterIdle - afterSettle <= EVENT_DRIVEN_IDLE_GRACE,
            "settled diagnostics must not re-publish on a timer; the publish count grew " +
                "by ${afterIdle - afterSettle} over a 20s idle window " +
                "(blind-poll regression — the unchanged-skip is not deduping)"
        )
    }

    /**
     * A valid csgs whose body sits in the `KcsgScript` receiver scope, so the engine offers
     * the DSL members there (e.g. `cube`, `export`). csgs line index 1 is `val c = cu` — a
     * partial reference we request completion on (the cursor sits right after `cu`).
     */
    private val completionScript = """
        val ok by primitive { cube { dimensions = xyz(1.0, 1.0, 1.0) } }
        export("ok")
    """.trimIndent()

    /**
     * Phase 4 (#39) request-position translation, end-to-end against the real engine.
     * Completion at a csgs cursor inside the receiver scope must offer kcsg DSL members
     * (here `cube`). Proves the cursor Position is translated csgs → wrapped before the
     * engine sees it (otherwise the engine would complete in the header/wrong scope).
     */
    @Test
    fun realEngineOffersKcsgCompletion() = runBlocking {
        val config = env!!.config
        val workspaceId = "0"
        val konstructionId = "0"
        val paths: PathController.Paths = PathController(config)[workspaceId, konstructionId]
        paths.contentFile.writeText(completionScript)
        val frontendUri = "file:///$workspaceId/$konstructionId/content.csgs"

        val bridge = BridgeLanguageServer(
            config,
            workspaceId,
            konstructionId,
            object : DefaultLanguageClient() {}
        )
        bridge.initialize(
            InitializeParams(
                capabilities = ClientCapabilities(),
                processId = ProcessHandle.current().pid().toInt(),
                rootUri = "file:///$workspaceId"
            )
        )
        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(
            DidOpenTextDocumentParams(
                textDocument = TextDocumentItem(
                    uri = frontendUri,
                    languageId = "kotlin",
                    version = 1,
                    text = completionScript
                )
            )
        )

        // Request completion at the END of csgs line 0 (after `... } }`), inside the
        // KcsgScript receiver scope. The cursor is in CSGS space; the bridge must add the
        // header to reach the right wrapped-.kt line. Poll while the index warms.
        val cursor = Position(
            line = 0u,
            character = completionScript.lines().first().length.toUInt()
        )
        val labels = withTimeoutOrNull(180_000) {
            while (true) {
                val result = runCatching {
                    bridge.textDocumentCompletion(
                        CompletionParams(
                            textDocument = TextDocumentIdentifier(uri = frontendUri),
                            position = cursor
                        )
                    )
                }.getOrNull()
                val items = result?.let { completionLabels(it) } ?: emptyList()
                if (items.any { it == "cube" }) return@withTimeoutOrNull items
                delay(2000)
            }
            @Suppress("UNREACHABLE_CODE")
            emptyList<String>()
        }

        bridge.shutdown()
        bridge.exit()

        assertTrue(labels != null, "no completion ever returned `cube` within budget")
        System.err.println("REAL-ENGINE completion offered ${labels.size} items")
        assertTrue(
            labels.contains("cube"),
            "expected the kcsg DSL member `cube` in the receiver scope; got: " +
                labels.take(40).joinToString(", ")
        )
    }

    /**
     * Phase 4 (#39) hover: hovering a kcsg symbol must resolve to non-empty content, and any
     * returned range must already be in CSGS space (line < the header offset would mean the
     * response range wasn't translated back down).
     */
    @Test
    fun realEngineResolvesKcsgHover() = runBlocking {
        val config = env!!.config
        val workspaceId = "0"
        val konstructionId = "0"
        val paths: PathController.Paths = PathController(config)[workspaceId, konstructionId]
        paths.contentFile.writeText(completionScript)
        val frontendUri = "file:///$workspaceId/$konstructionId/content.csgs"

        val bridge = BridgeLanguageServer(
            config,
            workspaceId,
            konstructionId,
            object : DefaultLanguageClient() {}
        )
        bridge.initialize(
            InitializeParams(
                capabilities = ClientCapabilities(),
                processId = ProcessHandle.current().pid().toInt(),
                rootUri = "file:///$workspaceId"
            )
        )
        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(
            DidOpenTextDocumentParams(
                textDocument = TextDocumentItem(
                    uri = frontendUri,
                    languageId = "kotlin",
                    version = 1,
                    text = completionScript
                )
            )
        )

        // Hover over the `cube` token on csgs line 0 (its index in the first line).
        val cubeColumn = completionScript.lines().first().indexOf("cube") + 1
        val cursor = Position(line = 0u, character = cubeColumn.toUInt())
        val hover = withTimeoutOrNull(180_000) {
            while (true) {
                val h = runCatching {
                    bridge.textDocumentHover(
                        HoverParams(
                            textDocument = TextDocumentIdentifier(uri = frontendUri),
                            position = cursor
                        )
                    )
                }.getOrNull()
                if (h != null && h.contents.toString().length > 2) return@withTimeoutOrNull h
                delay(2000)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

        // Whitespace-hover suppression: hovering empty space inside the user's content makes the
        // engine resolve the hover to the ENCLOSING wrapper construct (the `KcsgScript().apply { }`
        // lambda the csgs is spliced into), whose range lands on a header line. That used to leak
        // through as a contentful tooltip with a null (untranslatable) range — the spurious
        // "wrapper" hover the user reported. The bridge must now SUPPRESS it (return null). Probe a
        // column well past the end of csgs line 1 (`export("ok")`), which is pure whitespace.
        val pastEol = completionScript.lines()[1].length + 8
        val whitespaceHover = runCatching {
            bridge.textDocumentHover(
                HoverParams(
                    textDocument = TextDocumentIdentifier(uri = frontendUri),
                    position = Position(line = 1u, character = pastEol.toUInt())
                )
            )
        }.getOrNull()
        System.err.println("REAL-ENGINE whitespace hover: $whitespaceHover")

        bridge.shutdown()
        bridge.exit()

        assertTrue(hover != null, "hover never resolved kcsg content within budget")
        System.err.println("REAL-ENGINE hover range: ${hover.range}")
        val range = hover.range
        // If the engine returned a range it must be translated to csgs space: the `cube`
        // token is on csgs line 0, so the range must NOT sit at header line N.
        if (range != null) {
            assertTrue(
                range.start.line.toInt() == 0,
                "hover range must be translated to csgs space (line 0), got ${range.start.line}"
            )
        }
        // The whitespace hover must never surface a wrapper tooltip: either no hover at all, or a
        // hover whose range is genuinely in csgs space. A contentful hover with a null range is the
        // exact leak we fixed.
        if (whitespaceHover != null) {
            assertTrue(
                whitespaceHover.range != null,
                "whitespace hover returned content with no csgs range — the wrapper tooltip leaked"
            )
        }
    }

    private fun completionLabels(result: TextDocumentCompletionResult): List<String> =
        when (result) {
            is TextDocumentCompletionResult.CompletionItemArray -> result.value.map { it.label }

            is TextDocumentCompletionResult.CompletionListValue ->
                result.value.items.map { it.label }
        }

    private companion object {
        /** The `val broken = thisSymbolDoesNotExist123` line in [deliberateErrorScript]. */
        private const val EXPECTED_CSGS_ERROR_LINE = 1

        /**
         * Max extra publishes tolerated over a 20s idle window once diagnostics settle: a
         * straggler backoff pull or a single engine-driven refresh is fine; a steady stream
         * (the old 15s blind-poll, ~1+ per interval) is a regression.
         */
        private const val EVENT_DRIVEN_IDLE_GRACE = 1
    }
}
