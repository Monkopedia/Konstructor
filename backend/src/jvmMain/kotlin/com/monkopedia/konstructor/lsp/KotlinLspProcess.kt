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

    /** Spawn timestamps inside the current window, used to bound respawn attempts. */
    private val recentSpawns = ArrayDeque<Long>()

    /** While `now < this`, spawning is refused outright and the bridge stays inert. */
    private var cooldownUntil = 0L

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

    /**
     * Internal rather than private so a test can drive the spawn path directly, without
     * needing a [KsrpcLanguageClient] forwarder for a subprocess that is never going to
     * answer. Not part of the public surface.
     */
    internal suspend fun ensureConnection(): SingleChannelConnection<String>? {
        connection?.let { existing ->
            if (process?.isAlive == true) return existing
            // The warm process died; drop the stale handles and respawn below.
            shutdownLocked()
        }
        val binary = config.kotlinLspBinary ?: return null
        val now = System.currentTimeMillis()
        if (now < cooldownUntil) return null
        if (!admitSpawn(now)) return null
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
            if (!isStillRunning(proc)) {
                // An EXPIRED engine lands here (konstructor#84). It is present, executable,
                // and `start()` returns normally — it just prints "This build of
                // intellij-server has expired" and exits. Every other availability check
                // passes, so without this the connection is cached as live and the bridge
                // never degrades to inert.
                //
                // Deliberately "did it exit immediately" rather than "does stderr say
                // expired": it catches a bad launcher, a missing JRE and a wrong argument
                // the same way, and it does not depend on JetBrains' wording. A healthy
                // engine stays up for its ~120s cold index, so it is never confused with
                // one that dies in the first second.
                hauler.error(
                    "kotlin-lsp exited immediately (code ${proc.exitValue()}) — treating the " +
                        "engine as unavailable; LSP stays off. An expired build looks exactly " +
                        "like this."
                )
                runCatching { proc.destroyForcibly() }
                return null
            }
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

    /**
     * True if [proc] is still alive after a short readiness window.
     *
     * Costs [READY_PROBE_MILLIS] once per spawn, not per request — and only on the spawn
     * path, which already takes seconds. `waitFor` returns as soon as the process exits,
     * so the full wait is only paid by an engine that is actually staying up.
     */
    private fun isStillRunning(proc: Process): Boolean =
        !proc.waitFor(READY_PROBE_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)

    /**
     * Bound respawns. Without this, a dead-but-startable engine is re-spawned on every
     * editor event — and `textDocumentHover` fires on every cursor move, so "paced by
     * request traffic" means paced by how fast someone moves the mouse. Each attempt is a
     * fresh ~2GB JVM; #84 records that taking prod down for six days.
     */
    private suspend fun admitSpawn(now: Long): Boolean {
        while (recentSpawns.isNotEmpty() && now - recentSpawns.first() > SPAWN_WINDOW_MILLIS) {
            recentSpawns.removeFirst()
        }
        if (recentSpawns.size >= MAX_SPAWNS_PER_WINDOW) {
            cooldownUntil = now + COOLDOWN_MILLIS
            recentSpawns.clear()
            hauler.error(
                "kotlin-lsp failed to stay up $MAX_SPAWNS_PER_WINDOW times in " +
                    "${SPAWN_WINDOW_MILLIS / 1000}s — no further spawns for " +
                    "${COOLDOWN_MILLIS / 1000}s. LSP is off until then."
            )
            return false
        }
        recentSpawns.addLast(now)
        return true
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

        /** How long a freshly spawned engine must stay up to count as usable. */
        internal const val READY_PROBE_MILLIS = 1_500L

        /** Spawn attempts allowed inside [SPAWN_WINDOW_MILLIS] before the cooldown. */
        internal const val MAX_SPAWNS_PER_WINDOW = 3

        internal const val SPAWN_WINDOW_MILLIS = 60_000L
        internal const val COOLDOWN_MILLIS = 5 * 60_000L

        // One warm subprocess per Config (i.e. per backend). The classpath is fixed, so
        // a single instance serves all konstructions (Phase 5 hardens multiplexing).
        private val instances = mutableMapOf<Config, KotlinLspProcess>()

        @Synchronized
        fun forConfig(config: Config): KotlinLspProcess =
            instances.getOrPut(config) { KotlinLspProcess(config) }
    }
}
