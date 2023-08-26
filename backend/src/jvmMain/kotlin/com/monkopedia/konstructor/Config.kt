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

class Config {

    val executeTimeout: Duration
        get() = 5.minutes
    val cachingEnabled: Boolean
        get() = false
    val compilerOpts: String
        get() = ""
    val runtimeOpts: String
        get() = "-cp ${LibsJar.getLibsJar(this).absolutePath}"
    val json: Json = Json {
        ignoreUnknownKeys = true
    }
    val dataDir: File by lazy {
        File(File(System.getenv("HOME")), ".konstructor").also { it.mkdirs() }
    }
}
