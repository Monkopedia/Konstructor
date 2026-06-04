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
        )?.let(::File)
) {
    val cachingEnabled: Boolean
        get() = true
    val compilerOpts: String
        get() = ""
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
