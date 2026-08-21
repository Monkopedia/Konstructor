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
import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.info
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.sockets.asConnection
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        /**
         * Nothing from this process's plumbing may reach the global handler.
         *
         * [parentJob] is a [SupervisorJob] with no handler of its own, so a child that
         * throws does not cancel its siblings — it goes straight to the JVM's default
         * uncaught-exception handler instead, where nothing connects it back to this
         * class. In CI that surfaced as an unrelated test failing with
         * `UncaughtExceptionsBeforeTest` and a stack trace pointing at this file
         * (konstructor#101). Anything unexpected is logged here instead, naming its
         * actual source.
         */
        private val exceptionHandler = CoroutineExceptionHandler { _, thrown ->
            if (thrown !is CancellationException) {
                hauler.error("Uncaught failure in subprocess plumbing", thrown)
            }
        }
        val parentScope = CoroutineScope(
            parentJob + Dispatchers.IO + exceptionHandler + (
                CallSign.threadLoggingName?.let(::CallSign)
                    ?: EmptyCoroutineContext
                )
        )
        val exitCode = CompletableDeferred<Int>()
        val connection = CompletableDeferred<Connection<String>>()

        init {
            parentScope.launch {
                try {
                    proc.errorStream.copyTo(System.err)
                } catch (t: CancellationException) {
                    // That's fine.
                } catch (t: IOException) {
                    // Also fine, and the normal way this pump ends: the waitFor coroutine
                    // below closes the process streams once it exits, and a read that was
                    // blocked at that moment wakes with "Stream closed". The process ending
                    // is not an error, so catching only CancellationException here left an
                    // ordinary outcome escaping as an uncaught exception (konstructor#101).
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
                } catch (t: IOException) {
                    // Same shutdown race as the stderr pump above: the streams this is
                    // built on are closed when the process exits. Leave `connection`
                    // uncompleted — callers already handle a process that died before it
                    // could be attached to.
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
            // The command is `bash -c "kotlin ... ContentKt"`, and the `kotlin` launcher is
            // itself a bash script that forks `kotlinc`, which forks the actual `java` process.
            // destroyForcibly() only kills the top bash; the grandchild JVM survives, keeps the
            // stdout pipe open, and the host's ksrpc receive loop never sees EOF — so any
            // in-flight call() hangs indefinitely even though proc.waitFor() (the direct child)
            // has already returned. Kill the whole descendant tree so the pipe actually closes
            // and ksrpc#200's connection-death wakeup can fire. Destroy descendants before the
            // root so newly-forked children can't be reparented away mid-teardown.
            runCatching {
                proc.toHandle().descendants().forEach { it.destroyForcibly() }
            }
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
