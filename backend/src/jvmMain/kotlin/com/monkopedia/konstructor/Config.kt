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
package com.monkopedia.konstructor

import com.monkopedia.konstructor.tasks.LibsJar
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

class Config(
    dataDirFile: File = File(
        System.getenv("KONSTRUCTOR_HOME")
            ?: File(System.getenv("HOME"), ".konstructor").path
    ),
    val executeTimeout: Duration = 5.minutes,
    /**
     * Path to the JetBrains `kotlin-lsp` launcher (`bin/intellij-server`, run with
     * `--stdio`). LSP (epic #35) is wholly flag-gated AND requires this binary: when
     * it is `null` or missing, the backend never spawns the subprocess and LSP stays
     * off (the editor behaves exactly as without LSP). CI has no binary, so this is
     * unset there and the feature degrades to off — never a crash.
     *
     * Sourced from the `KONSTRUCTOR_KOTLIN_LSP` env var (or the
     * `konstructor.kotlinLsp` system property) by default.
     */
    val kotlinLspBinary: File? = (
        System.getenv("KONSTRUCTOR_KOTLIN_LSP")
            ?: System.getProperty("konstructor.kotlinLsp")
        )?.let(::File),
    /**
     * Deadline for a single request made to the kotlin-lsp subprocess. It covers EVERY
     * request-shaped call: the `initialize` handshake that decides whether a freshly spawned
     * engine is usable at all, the delegated editor requests (completion, hover,
     * signatureHelp), and the `textDocument/diagnostic` PULL that
     * [com.monkopedia.konstructor.lsp.PullDiagnosticsPublisher] drives. Notifications are not
     * covered and need no cover — they complete on the write.
     *
     * An engine that STAYS UP but never answers is indistinguishable from a healthy one until
     * something bounds the wait, and the editor simply hangs with LSP apparently on (#109).
     * The pull seam matters most, because it is the one a user notices first and the one with
     * no self-heal: the publisher's cold-index retry is a *cadence between completed pulls*,
     * not a deadline, so an unbounded pull parks it for the session.
     *
     * A healthy engine answers `initialize` in well under a second (indexing happens
     * afterwards) and a warm diagnostic pull in ~8s, so this is generous by design and only an
     * engine that is already broken reaches it. A pull that does hit the deadline is an
     * ordinary failed pull: it is logged and re-tried on the next cadence, which is strictly
     * better than the silence it replaces.
     *
     * Sourced (in seconds) from `KONSTRUCTOR_KOTLIN_LSP_TIMEOUT`, or the
     * `konstructor.kotlinLspTimeout` system property, like the other LSP knobs.
     */
    val kotlinLspCallTimeout: Duration = (
        System.getenv("KONSTRUCTOR_KOTLIN_LSP_TIMEOUT")
            ?: System.getProperty("konstructor.kotlinLspTimeout")
        )?.toLongOrNull()?.seconds ?: 30.seconds,
    /**
     * Whether the script host advertises STL caching to running scripts
     * ([com.monkopedia.konstructor.lib.HostService.supportsCaching]). Turning it off
     * makes every execution recompute its geometry, which is useful when diagnosing a
     * stale-cache result.
     */
    val cachingEnabled: Boolean = true,
    /**
     * Extra flags handed to `kotlinc` when compiling a konstruction, ahead of
     * [runtimeOpts]. Empty by default; whitespace-only values contribute nothing to the
     * command line.
     */
    val compilerOpts: String = ""
) {
    val runtimeOpts: String
        get() = "-cp ${LibsJar.getLibsJar(this).absolutePath} -J-Xmx4g -J-Xms4g"
    val json: Json = Json {
        ignoreUnknownKeys = true
    }
    val dataDir: File by lazy {
        dataDirFile.also { it.mkdirs() }
    }

    /**
     * Persistent index/cache dir for the warm kotlin-lsp subprocess (passed as
     * `--system-path`). Reusing it across runs keeps the ~120s cold index warm
     * (~8s warm diagnostics). Defaults under [dataDir] so it shares the data volume;
     * override with `KONSTRUCTOR_KOTLIN_LSP_SYSTEM_PATH` to point at a pre-warmed index
     * (ops can keep one warm across restarts).
     */
    val kotlinLspSystemPath: File by lazy {
        (
            System.getenv("KONSTRUCTOR_KOTLIN_LSP_SYSTEM_PATH")?.let(::File)
                ?: File(dataDir, "kotlin-lsp-system")
            ).also { it.mkdirs() }
    }

    /** True only when an LSP binary is configured AND present/executable on disk. */
    val isKotlinLspAvailable: Boolean
        get() = kotlinLspBinary?.let { it.exists() && it.canExecute() } == true
}
