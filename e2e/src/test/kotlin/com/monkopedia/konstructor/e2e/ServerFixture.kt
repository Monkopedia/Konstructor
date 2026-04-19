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
package com.monkopedia.konstructor.e2e

import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files

class ServerFixture {
    lateinit var baseUrl: String
        private set
    private lateinit var process: Process
    private lateinit var tempDir: File
    var port: Int = 0
        private set

    /** Path to `java` binary of the JVM currently running tests. */
    private val javaBinary: String =
        File(System.getProperty("java.home"), "bin/java").absolutePath

    fun start() {
        tempDir = Files.createTempDirectory("konstructor-e2e-").toFile()
        val dataDir = File(tempDir, ".konstructor")
        dataDir.mkdirs()
        port = ServerSocket(0).use { it.localPort }
        val jarPath = System.getProperty("konstructor.jar")
            ?: error("Set -Dkonstructor.jar to the backend shadow JAR path")

        val env = ProcessBuilder(
            javaBinary,
            "-jar",
            jarPath,
            "--http",
            port.toString(),
            "--cors",
            "--websockets"
        ).apply {
            environment()["KONSTRUCTOR_HOME"] = dataDir.absolutePath
        }.redirectErrorStream(true)

        process = env.start()
        baseUrl = "http://localhost:$port"

        // Consume stdout in background to prevent buffer blocking
        Thread {
            process.inputStream.bufferedReader().forEachLine {
                System.err.println("[server] $it")
            }
        }.apply { isDaemon = true }.start()

        waitForServer(timeoutMs = 30_000)
    }

    private fun waitForServer(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = URL("$baseUrl/").openConnection() as HttpURLConnection
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                val code = conn.responseCode
                if (code in 200..499) return
            } catch (_: Exception) {
                // Server not ready yet
            }
            Thread.sleep(500)
        }
        error("Server did not start within ${timeoutMs}ms")
    }

    /** Stop the server process but keep the data dir for restart. */
    fun stopProcess() {
        if (::process.isInitialized) {
            process.destroyForcibly()
            process.waitFor()
        }
    }

    /** Restart the server on the same port and data dir. */
    fun restart() {
        stopProcess()
        val jarPath = System.getProperty("konstructor.jar")
            ?: error("Set -Dkonstructor.jar to the backend shadow JAR path")
        val dataDir = File(tempDir, ".konstructor")
        process = ProcessBuilder(
            javaBinary,
            "-jar",
            jarPath,
            "--http",
            port.toString(),
            "--cors",
            "--websockets"
        ).apply {
            environment()["KONSTRUCTOR_HOME"] = dataDir.absolutePath
        }.redirectErrorStream(true).start()
        Thread {
            process.inputStream.bufferedReader().forEachLine {
                System.err.println("[server] $it")
            }
        }.apply { isDaemon = true }.start()
        waitForServer(timeoutMs = 30_000)
    }

    fun stop() {
        stopProcess()
        if (::tempDir.isInitialized) {
            tempDir.deleteRecursively()
        }
    }
}
