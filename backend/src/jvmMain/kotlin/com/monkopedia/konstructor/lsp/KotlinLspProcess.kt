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
import com.monkopedia.hauler.info
import com.monkopedia.konstructor.Config
import com.monkopedia.ksrpc.channels.SingleChannelConnection
import com.monkopedia.lsp.KsrpcLanguageClient
import com.monkopedia.lsp.KsrpcLanguageServer
import com.monkopedia.lsp.ksrpc.asLspConnection
import com.monkopedia.lsp.ksrpc.connectAsLspClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages ONE warm JetBrains `kotlin-lsp` (`intellij-server --stdio`) subprocess and
 * the dedicated stdio-LSP connection to it. This is the **backend↔subprocess leg** of
 * the Phase 2 bridge.
 *
 * The single managed instance is sufficient for Phase 2 because the classpath is fixed
 * (one `lib.jar`); true multi-konstruction multiplexing + keep-warm tuning is Phase 5
 * (#40). We spawn lazily on first use, persist the index with `--system-path` (so the
 * ~120s cold index warms once and later sessions get ~8s diagnostics), and wrap the
 * child's stdin/stdout with lsp-ksrpc's [asLspConnection] — the dedicated-connection
 * path those helpers are for.
 *
 * The subprocess-facing [KsrpcLanguageServer] stub is obtained via
 * [connectAsLspClient], passing a [forwarder] [KsrpcLanguageClient] (constructed by the
 * caller) whose `textDocumentPublishDiagnostics` forwards to the frontend editor.
 *
 * Everything here is gated on [Config.isKotlinLspAvailable]: if the binary is
 * unset/missing, [start] returns `null` and LSP stays off (never a crash).
 */
class KotlinLspProcess private constructor(private val config: Config) {
    private val hauler = hauler("KotlinLspProcess")
    private val lock = Mutex()

    private var process: Process? = null
    private var connection: SingleChannelConnection<String>? = null

    /**
     * Lazily spawn (once) the warm subprocess and return a fresh subprocess-facing
     * [KsrpcLanguageServer] stub wired to [forwarder]. Returns `null` if the binary is
     * not available, or if spawning/connecting fails (LSP degrades to off).
     *
     * The subprocess itself is a singleton; each call returns a stub bound to the given
     * forwarder so server→client pushes (diagnostics) for THIS session land on the right
     * editor. For Phase 2 we scope to the active konstruction, so a single warm process
     * suffices.
     */
    suspend fun connect(forwarder: KsrpcLanguageClient): KsrpcLanguageServer? {
        if (!config.isKotlinLspAvailable) return null
        return lock.withLock {
            val conn = ensureConnection() ?: return@withLock null
            try {
                conn.connectAsLspClient(forwarder)
            } catch (t: Throwable) {
                hauler.error("Failed to connect to kotlin-lsp subprocess", t)
                null
            }
        }
    }

    private suspend fun ensureConnection(): SingleChannelConnection<String>? {
        connection?.let { existing ->
            if (process?.isAlive == true) return existing
            // The warm process died; drop the stale handles and respawn below.
            shutdownLocked()
        }
        val binary = config.kotlinLspBinary ?: return null
        return try {
            val systemPath = config.kotlinLspSystemPath.absolutePath
            hauler.info("Spawning kotlin-lsp: ${binary.absolutePath} (system-path=$systemPath)")
            val proc = ProcessBuilder(
                binary.absolutePath,
                "--stdio",
                "--system-path",
                systemPath
            ).redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
            val conn = (proc.inputStream to proc.outputStream).asLspConnection()
            process = proc
            connection = conn
            conn
        } catch (t: Throwable) {
            hauler.error("Failed to spawn kotlin-lsp subprocess", t)
            shutdownLocked()
            null
        }
    }

    /** Tear the subprocess down (best-effort, bounded). Safe to call multiple times. */
    suspend fun shutdown() = lock.withLock { shutdownLocked() }

    private fun shutdownLocked() {
        val proc = process
        process = null
        connection = null
        if (proc != null && proc.isAlive) {
            runCatching { proc.outputStream.close() }
            runCatching { proc.inputStream.close() }
            proc.destroy()
            if (!proc.waitFor(GRACE_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly()
            }
        }
    }

    companion object {
        private const val GRACE_MILLIS = 1_000L

        // One warm subprocess per Config (i.e. per backend). The classpath is fixed, so
        // a single instance serves all konstructions (Phase 5 hardens multiplexing).
        private val instances = mutableMapOf<Config, KotlinLspProcess>()

        @Synchronized
        fun forConfig(config: Config): KotlinLspProcess =
            instances.getOrPut(config) { KotlinLspProcess(config) }
    }
}
