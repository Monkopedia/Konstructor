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

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.tasks.LibsJar
import com.monkopedia.konstructor.testutil.TestEnvironment
import com.monkopedia.lsp.ClientCapabilities
import com.monkopedia.lsp.CompletionItem
import com.monkopedia.lsp.CompletionParams
import com.monkopedia.lsp.DefaultLanguageClient
import com.monkopedia.lsp.DefaultLanguageServer
import com.monkopedia.lsp.DiagnosticOptions
import com.monkopedia.lsp.DidOpenTextDocumentParams
import com.monkopedia.lsp.DocumentDiagnosticParams
import com.monkopedia.lsp.DocumentDiagnosticReport
import com.monkopedia.lsp.InitializeParams
import com.monkopedia.lsp.InitializeResult
import com.monkopedia.lsp.InitializedParams
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.RelatedFullDocumentDiagnosticReport
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.ServerCapabilitiesDiagnosticProvider
import com.monkopedia.lsp.TextDocumentCompletionResult
import com.monkopedia.lsp.TextDocumentIdentifier
import com.monkopedia.lsp.TextDocumentItem
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Self-heal-after-crash guard for [BridgeLanguageServer] (#54), exercised WITHOUT a real
 * kotlin-lsp engine: the delegate-acquisition seam ([BridgeLanguageServer.connectEngine]) is
 * overridden to hand out fake in-process delegates, so a crash → respawn transition can be driven
 * deterministically. This is the CI-runnable regression coverage for the reconnect logic (the
 * real-engine half is gated in [com.monkopedia.konstructor.integration.BridgeLanguageServerIntegrationTest]).
 *
 * The behaviours pinned:
 *  - a forward call against a CRASHED delegate transparently reconnects to a fresh delegate and
 *    retries once (LSP resumes without a file reopen),
 *  - the reconnect replays the engine handshake (`initialize` + `initialized`) and re-`didOpen`s the
 *    CURRENT document,
 *  - concurrent failing calls single-flight the reconnect (one respawn, not N),
 *  - after teardown the bridge never resurrects the delegate, and
 *  - the replaced connection's scope is cancelled, so work launched against the dead engine (the
 *    diagnostics pull) stops there and then rather than running on (#80).
 */
class BridgeLanguageServerReconnectTest {

    private val env = TestEnvironment()
    private val workspaceId = "0"
    private val konstructionId = "0"
    private val frontendUri = "file:///$workspaceId/$konstructionId/content.csgs"

    init {
        // The bridge synthesizes a workspace.json that references the extracted lib.jar. In a unit
        // test the `/lib-all.raj` classpath resource isn't present, so pre-seed LibsJar's cache with
        // a throwaway (empty) jar — the fake engine never reads it; we only need synthesize() to
        // complete without hitting the missing resource.
        val fakeLib = env.tempDir.resolve("fake-lib.jar")
        ZipOutputStream(fakeLib.outputStream()).close()
        LibsJar::class.java.getDeclaredField("libsFile").apply { isAccessible = true }
            .set(LibsJar, fakeLib)
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    /** A fake engine stub. Forward calls throw while [crashed]; otherwise they record + respond. */
    private class FakeEngine(
        val id: Int,
        val crashed: AtomicBoolean = AtomicBoolean(false),
        /** Non-null ⇒ the fake advertises pull mode, so the bridge stands up a publisher. */
        private val diagnosticProvider: ServerCapabilitiesDiagnosticProvider? = null
    ) : DefaultLanguageServer() {
        val initializes = CopyOnWriteArrayList<InitializeParams>()
        val initializedCalls = CopyOnWriteArrayList<InitializedParams>()
        val didOpens = CopyOnWriteArrayList<DidOpenTextDocumentParams>()
        val completions = CopyOnWriteArrayList<CompletionParams>()

        /** Completes once a diagnostics pull has reached this engine and parked on the gate. */
        val pullStarted = CompletableDeferred<Unit>()

        /** Completes if a parked pull is CANCELLED — i.e. the work scoped to it was stopped. */
        val pullCancelled = CompletableDeferred<Unit>()

        /** Never completed: a pull parks here so the test can observe what happens to it. */
        private val pullGate = CompletableDeferred<Unit>()

        private fun crashIfDead() {
            if (crashed.get()) error("fake kotlin-lsp #$id crashed")
        }

        override suspend fun initialize(params: InitializeParams): InitializeResult {
            crashIfDead()
            initializes.add(params)
            return InitializeResult(
                capabilities = ServerCapabilities(diagnosticProvider = diagnosticProvider)
            )
        }

        /**
         * A pull that never answers: it records that it started and then parks, so the test can
         * assert whether the coroutine driving it is still alive after a reconnect.
         */
        override suspend fun textDocumentDiagnostic(
            params: DocumentDiagnosticParams
        ): DocumentDiagnosticReport {
            crashIfDead()
            pullStarted.complete(Unit)
            try {
                pullGate.await()
            } catch (e: CancellationException) {
                pullCancelled.complete(Unit)
                throw e
            }
            return RelatedFullDocumentDiagnosticReport(kind = "full", items = emptyList())
        }

        override suspend fun initialized(params: InitializedParams) {
            crashIfDead()
            initializedCalls.add(params)
        }

        override suspend fun textDocumentDidOpen(params: DidOpenTextDocumentParams) {
            crashIfDead()
            didOpens.add(params)
        }

        override suspend fun textDocumentCompletion(
            params: CompletionParams
        ): TextDocumentCompletionResult? {
            crashIfDead()
            completions.add(params)
            return TextDocumentCompletionResult.CompletionItemArray(
                listOf(CompletionItem(label = "cube"))
            )
        }
    }

    /** A bridge whose engine seam yields scripted fakes instead of spawning a subprocess. */
    private class FakeEngineBridge(
        config: Config,
        workspaceId: String,
        konstructionId: String,
        private val engines: () -> FakeEngine?
    ) : BridgeLanguageServer(
        config,
        workspaceId,
        konstructionId,
        object : DefaultLanguageClient() {}
    ) {
        val handedOut = CopyOnWriteArrayList<FakeEngine>()

        /**
         * Mirrors [KotlinLspProcess.connect]: acquiring an engine INCLUDES driving it through
         * `initialize`, so the seam hands back a session only for an engine that answered.
         */
        override suspend fun connectEngine(params: InitializeParams): EngineSession? {
            val engine = engines() ?: return null
            handedOut.add(engine)
            // ...including the swallow: a handshake that throws (or never answers) means the
            // engine is unusable, and the real connect() reports that as null rather than
            // letting it escape. The bridge's initialize/reconnect now call this OUTSIDE any
            // guard, so a propagating fake would test a contract production does not have.
            return runCatching { EngineSession(engine, engine.initialize(params)) }.getOrNull()
        }
    }

    private fun seedContent(text: String) {
        val paths: PathController.Paths =
            PathController(env.config)[workspaceId, konstructionId]
        paths.contentFile.parentFile.mkdirs()
        paths.contentFile.writeText(text)
    }

    private fun completionAt(): CompletionParams = CompletionParams(
        textDocument = TextDocumentIdentifier(uri = frontendUri),
        position = Position(line = 0u, character = 0u)
    )

    private fun openParams(text: String) = DidOpenTextDocumentParams(
        textDocument = TextDocumentItem(
            uri = frontendUri,
            languageId = "kotlin",
            version = 1,
            text = text
        )
    )

    @Test
    fun `a crashed delegate self-heals on the next request and retries once`() = runBlocking {
        seedContent("val a = 1\n")
        val first = FakeEngine(id = 1)
        val second = FakeEngine(id = 2)
        val queue = ArrayDeque(listOf(first, second))
        val bridge = FakeEngineBridge(env.config, workspaceId, konstructionId) {
            queue.removeFirstOrNull()
        }

        // First open drives handshake + didOpen against the first fake.
        bridge.initialize(
            InitializeParams(capabilities = ClientCapabilities(), rootUri = "file:///$workspaceId")
        )
        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(openParams("val a = 1\n"))
        assertEquals(1, first.didOpens.size, "first engine got the didOpen")

        // The engine crashes; the NEXT completion must reconnect to the second fake and retry.
        first.crashed.set(true)
        val result = bridge.textDocumentCompletion(completionAt())

        assertTrue(
            result is TextDocumentCompletionResult.CompletionItemArray,
            "completion must succeed against the reconnected engine, not stay inert"
        )
        assertEquals(2, bridge.handedOut.size, "exactly one reconnect happened (one new engine)")
        // The reconnect replayed the handshake + didOpen against the fresh engine...
        assertEquals(1, second.initializes.size, "reconnect replayed initialize on the new engine")
        assertEquals(1, second.initializedCalls.size, "reconnect replayed initialized")
        assertEquals(1, second.didOpens.size, "reconnect replayed the current document's didOpen")
        // ...and the retried completion landed on the fresh engine.
        assertEquals(
            1,
            second.completions.size,
            "the retried completion hit the reconnected engine"
        )
    }

    @Test
    fun `reconnect replays the CURRENT edited content not the stale open text`() = runBlocking {
        seedContent("val a = 1\n")
        val first = FakeEngine(id = 1)
        val second = FakeEngine(id = 2)
        val queue = ArrayDeque(listOf(first, second))
        val bridge = FakeEngineBridge(env.config, workspaceId, konstructionId) {
            queue.removeFirstOrNull()
        }
        bridge.initialize(
            InitializeParams(capabilities = ClientCapabilities(), rootUri = "file:///$workspaceId")
        )
        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(openParams("val a = 1\n"))

        // Edit the document, then crash + trigger reconnect.
        bridge.textDocumentDidChange(
            com.monkopedia.lsp.DidChangeTextDocumentParams(
                textDocument = com.monkopedia.lsp.VersionedTextDocumentIdentifier(
                    uri = frontendUri,
                    version = 2
                ),
                contentChanges = listOf(
                    com.monkopedia.lsp.TextDocumentContentChangeEventVariant(
                        text = "val edited = 42\n"
                    )
                )
            )
        )
        first.crashed.set(true)
        bridge.textDocumentCompletion(completionAt())

        // The replayed didOpen must carry the EDITED content (wrapped), not the original.
        val replayed = second.didOpens.single().textDocument.text
        assertTrue(
            replayed.contains("val edited = 42"),
            "reconnect must re-didOpen the current edited content; got: $replayed"
        )
    }

    @Test
    fun `after teardown the bridge does not resurrect the delegate`() = runBlocking {
        seedContent("val a = 1\n")
        val first = FakeEngine(id = 1)
        val second = FakeEngine(id = 2)
        val queue = ArrayDeque(listOf(first, second))
        val bridge = FakeEngineBridge(env.config, workspaceId, konstructionId) {
            queue.removeFirstOrNull()
        }
        bridge.initialize(
            InitializeParams(capabilities = ClientCapabilities(), rootUri = "file:///$workspaceId")
        )
        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(openParams("val a = 1\n"))

        // Full teardown (shutdown→exit→close).
        bridge.shutdown()
        bridge.exit()
        bridge.close()

        // A forward call after teardown must stay inert AND must not spawn a fresh engine.
        val result = bridge.textDocumentCompletion(completionAt())
        assertNull(result, "a torn-down bridge stays inert")
        assertEquals(
            1,
            bridge.handedOut.size,
            "teardown must prevent any reconnect (no second engine handed out)"
        )
    }

    @Test
    fun `reconnect cancels the work scoped to the dead connection`() = runBlocking {
        seedContent("val a = 1\n")
        val pullMode =
            DiagnosticOptions(interFileDependencies = false, workspaceDiagnostics = false)
        val first = FakeEngine(id = 1, diagnosticProvider = pullMode)
        val second = FakeEngine(id = 2, diagnosticProvider = pullMode)
        val queue = ArrayDeque(listOf(first, second))
        val bridge = FakeEngineBridge(env.config, workspaceId, konstructionId) {
            queue.removeFirstOrNull()
        }
        bridge.initialize(
            InitializeParams(capabilities = ClientCapabilities(), rootUri = "file:///$workspaceId")
        )
        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(openParams("val a = 1\n"))
        // The first connection's publisher is now mid-pull against the first engine.
        withTimeout(10.seconds) { first.pullStarted.await() }

        // The engine crashes and the next request self-heals onto the second engine.
        first.crashed.set(true)
        bridge.textDocumentCompletion(completionAt())

        // The dead connection's scope goes with it, so its in-flight pull is cancelled instead of
        // polling on against the corpse until some private countdown expires (#80)...
        withTimeout(10.seconds) { first.pullCancelled.await() }
        // ...and the pulling has moved to the fresh connection.
        withTimeout(10.seconds) { second.pullStarted.await() }

        bridge.close()
    }

    @Test
    fun `when the respawn keeps failing the bridge stays inert without throwing`() = runBlocking {
        seedContent("val a = 1\n")
        val first = FakeEngine(id = 1)
        // After the first engine, connectEngine yields null (respawn unavailable).
        val queue = ArrayDeque(listOf<FakeEngine>(first))
        val bridge = FakeEngineBridge(env.config, workspaceId, konstructionId) {
            queue.removeFirstOrNull()
        }
        bridge.initialize(
            InitializeParams(capabilities = ClientCapabilities(), rootUri = "file:///$workspaceId")
        )
        bridge.initialized(InitializedParams())
        bridge.textDocumentDidOpen(openParams("val a = 1\n"))

        first.crashed.set(true)
        // Must not throw (no cascade) — just return the inert fallback.
        val result = bridge.textDocumentCompletion(completionAt())
        assertNull(result, "a still-dead engine degrades to inert, never throwing out")
    }
}
