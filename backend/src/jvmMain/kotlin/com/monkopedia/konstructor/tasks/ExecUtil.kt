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

import com.monkopedia.hauler.CallSign
import com.monkopedia.hauler.asAsync
import com.monkopedia.hauler.debug
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.info
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.EmptyCoroutineContext

object ExecUtil {
    @OptIn(DelicateCoroutinesApi::class)
    private val hauler by lazy { hauler().asAsync(GlobalScope) }

    data class ExecResult(
        val stdOut: BufferedReader,
        val stdErr: BufferedReader,
        val returnCode: Int
    )

    fun executeAndWait(command: String): ExecResult {
        val rt = Runtime.getRuntime()
        hauler.debug("Executing: $command")
        val commands = arrayOf(
            "bash",
            "-c",
            command
        )
        val proc = rt.exec(commands)
        return ExecResult(
            BufferedReader(InputStreamReader(proc.inputStream)),
            BufferedReader(InputStreamReader(proc.errorStream)),
            proc.waitFor()
        ).also {
            hauler.debug("Done executing: $command (${it.returnCode})")
        }
    }

    class ExecProcess(private val proc: Process) {
        private val parentJob = SupervisorJob()
        val parentScope = CoroutineScope(
            parentJob + Dispatchers.IO + (
                CallSign.threadLoggingName?.let(::CallSign)
                    ?: EmptyCoroutineContext
                )
        )
        val exitCode = CompletableDeferred<Int>()
        val connection = CompletableDeferred<Connection>()

        init {
            parentScope.launch {
                try {
                    proc.errorStream.copyTo(System.err)
                } catch (t: CancellationException) {
                    // That's fine.
                }
            }
            parentScope.launch {
                try {
                    connection.complete(
                        (proc.inputStream to proc.outputStream).asConnection(
                            ksrpcEnvironment {
                                errorListener = ErrorListener { t ->
                                    if (t !is CancellationException) {
                                        t.printStackTrace()
                                    }
                                }
                            }
                        )
                    )
                } catch (t: CancellationException) {
                    // That's fine.
                }
            }
            parentScope.launch {
                val returnCode = withContext(Dispatchers.IO) {
                    proc.waitFor()
                }.also {
                    proc.errorStream.close()
                    proc.outputStream.close()
                    proc.inputStream.close()
                }
                exitCode.complete(returnCode)
                hauler.debug("Done executing: ($returnCode)")
                parentJob.cancel()
            }
        }

        fun kill() {
            proc.destroyForcibly()
        }
    }

    fun executeWithChannel(command: String): ExecProcess {
        val rt = Runtime.getRuntime()
        hauler.info("Executing: $command")
        val commands = arrayOf("bash", "-c", command)
        return ExecProcess(rt.exec(commands))
    }
}
