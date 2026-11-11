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
package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.Config
import java.io.File
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

object LibsJar {
    private var libsFile: File? = null
    private var identity: Pair<File, String>? = null

    fun getLibsJar(config: Config): File = libsFile ?: createLibsFile(config).also {
        libsFile = it
    }

    /**
     * A content hash of the lib jar scripts run against: kcsg plus konstructor's own lib, i.e.
     * everything that turns a script into geometry. Render-cache entries are keyed on it (#132)
     * so a library upgrade misses instead of serving geometry the old library produced.
     */
    fun getLibsIdentity(config: Config): String {
        val jar = getLibsJar(config)
        identity?.takeIf { it.first == jar }?.let { return it.second }
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(jar.inputStream(), digest).use {
            it.copyTo(OutputStream.nullOutputStream())
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return hex.take(16).also { identity = jar to it }
    }

    private fun createLibsFile(config: Config): File =
        File(config.dataDir, "lib.jar").also { libFile ->
            if (!libFile.parentFile.exists()) {
                libFile.parentFile.mkdirs()
            }
            libFile.outputStream().use { output ->
                this::class.java.getResourceAsStream("/lib-all.raj").use {
                    it.copyTo(output)
                }
            }
        }
}
