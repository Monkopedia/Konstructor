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

import com.monkopedia.hauler.Shipper
import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionListener
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.testutil.TestEnvironment
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.toStub
import com.monkopedia.lsp.DefaultLanguageClient
import com.monkopedia.lsp.KsrpcLanguageClient
import com.monkopedia.lsp.KsrpcLanguageServer
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * The sub-service / reverse-channel **leak test** for the LSP harden phase (epic #35 / #40),
 * mirroring ksrpc's own `RpcSubserviceLeakTest`. This is the single most valuable guard the
 * lsp-kotlin maintainer asked for: it catches the whole leak class (incl. the uri-key leak)
 * at the KSRPC layer.
 *
 * **Why it leaks without teardown.** Our editor↔backend WebSocket is long-lived and SHARED
 * across konstructions, so a nested ksrpc sub-service stays registered until it is explicitly
 * `close()`d (or the whole connection closes). `KonstructionService.lsp(client)` is bidi: it
 * RETURNS a [KsrpcLanguageServer] sub-service AND ACCEPTS a [KsrpcLanguageClient] sub-service
 * (the reverse `publishDiagnostics` channel). So every editor open→close cycle that does NOT
 * tear down leaks TWO sub-channels.
 *
 * **What the fix does.** On editor/konstruction close the frontend does a `shutdown`→`exit`
 * handshake then `close()`s the returned server stub; closing the stub releases the `lsp()`
 * sub-service AND drives the backend [BridgeLanguageServer.close], which `close()`s the
 * stashed reverse [KsrpcLanguageClient] (releasing its reverse channel).
 *
 * **CI-runnable, no engine binary.** This exercises only the ksrpc layer over an in-memory
 * duplex [Connection]; the bridge runs its inert path (no `kotlin-lsp` binary on CI, so
 * [Config.isKotlinLspAvailable] is false). The proof points, asserted to return to baseline
 * after N cycles (no monotonic growth):
 *  - the local reverse client's `close()` fires once per cycle ⇒ the reverse channel is
 *    released. (Because `frontendClient.close()` is reached ONLY inside
 *    [BridgeLanguageServer.close], this also proves the returned `lsp()` server sub-service
 *    was itself closed — i.e. it left the host channel's serviceMap and did not leak.)
 */
class LspSubserviceLeakTest {

    private lateinit var env: TestEnvironment

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    /**
     * Minimal [KonstructionService] whose only live method is [lsp]; every other method is
     * irrelevant to the leak and errors if touched. [lsp] returns a real (inert) bridge so the
     * production teardown path is exercised end to end.
     */
    private inner class LeakKonstructionService(private val config: Config) : KonstructionService {
        override suspend fun lsp(client: KsrpcLanguageClient): KsrpcLanguageServer =
            BridgeLanguageServer(config, "0", "0", client)

        override suspend fun getName(u: Unit): String = error("unused")
        override suspend fun setName(name: String) = error("unused")
        override suspend fun getInfo(u: Unit): KonstructionInfo = error("unused")
        override suspend fun fetch(u: Unit): String = error("unused")
        override suspend fun set(content: String) = error("unused")
        override suspend fun setBinary(content: ByteReadChannel) = error("unused")
        override suspend fun register(listener: KonstructionListener): String = error("unused")
        override suspend fun unregister(key: String): Boolean = error("unused")
        override suspend fun konstructed(target: String): String? = error("unused")
        override suspend fun compile(u: Unit): TaskResult = error("unused")
        override suspend fun requestCompile(u: Unit) = error("unused")
        override suspend fun konstruct(target: String): TaskResult = error("unused")
        override suspend fun requestKonstruct(target: String) = error("unused")
        override suspend fun requestKonstructs(targets: List<String>) = error("unused")
        override suspend fun getShipper(u: Unit): Shipper = error("unused")
    }

    @Test
    fun openCloseLspSubserviceDoesNotLeak() = runBlocking<Unit> {
        val config = env.config
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // Two in-memory byte pipes form one duplex Connection. `serverToClient` carries server
        // writes / client reads; `clientToServer` the reverse.
        val serverToClient = ByteChannel(autoFlush = true)
        val clientToServer = ByteChannel(autoFlush = true)
        val serverEnv = ksrpcEnvironment { }
        val clientEnv = ksrpcEnvironment { }

        val serverConnection: Connection<String> =
            (clientToServer as ByteReadChannel to serverToClient as ByteWriteChannel)
                .asConnection(scope, serverEnv)
        val clientConnection: Connection<String> =
            (serverToClient as ByteReadChannel to clientToServer as ByteWriteChannel)
                .asConnection(scope, clientEnv)

        serverConnection.registerDefault<KonstructionService, String>(
            LeakKonstructionService(config)
        )
        val stub = clientConnection.defaultChannel().toStub<KonstructionService, String>()

        // Each local reverse client records its own close() — the observable proof that the
        // reverse channel was released when the backend close()d its stashed client stub.
        val reverseClientCloses = AtomicInteger(0)

        val iterations = 25
        repeat(iterations) {
            val reverseClient = object : DefaultLanguageClient() {
                override suspend fun close() {
                    reverseClientCloses.incrementAndGet()
                }
            }
            // OPEN: the nested lsp() sub-service returns a server stub and registers the
            // reverse client sub-service on the same duplex connection.
            val server = stub.lsp(reverseClient)
            // A real handshake so both sub-services are fully established before teardown.
            server.shutdown()
            server.exit()
            // CLOSE: releases the lsp() server sub-service; the backend bridge.close() then
            // close()s the stashed reverse client, which round-trips a close back to the local
            // reverseClient above.
            server.close()
        }

        // Let the close frames for the reverse channels round-trip.
        withTimeout(20_000) {
            while (reverseClientCloses.get() < iterations) {
                delay(10)
            }
        }

        // Reverse channel released every cycle (count returns to baseline, no growth). This is
        // also proof the lsp() server sub-service was closed (bridge.close() runs frontendClient
        // .close()), so neither of the two per-cycle sub-channels leaked.
        assertEquals(
            iterations,
            reverseClientCloses.get(),
            "every open lsp() must release its reverse KsrpcLanguageClient channel on close"
        )

        runCatching { stub.close() }
        runCatching { serverConnection.close() }
        runCatching { clientConnection.close() }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
