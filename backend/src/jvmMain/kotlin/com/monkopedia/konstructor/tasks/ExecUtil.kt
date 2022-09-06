package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.lib.createConnection
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.ksrpcEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

object ExecUtil {

    data class ExecResult(
        val stdOut: BufferedReader,
        val stdErr: BufferedReader,
        val returnCode: Int
    )

    fun executeAndWait(command: String): ExecResult {
        val rt = Runtime.getRuntime()
        println("Executing: $command")
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
            println("Done executing: $command (${it.returnCode})")
        }
    }

    suspend fun executeWithChannel(
        command: String,
        withConnection: suspend (Connection) -> Unit
    ): Int {
        val rt = Runtime.getRuntime()
        println("Executing: $command")
        val commands = arrayOf(
            "bash",
            "-c",
            command
        )
        val returnCode = coroutineScope {
            val jobs = mutableListOf<Job>()
            val proc = rt.exec(commands)
            jobs.add(
                launch(Dispatchers.IO) {
                    try {
                        proc.errorStream.copyTo(System.err)
                    } catch (t: CancellationException) {
                        // That's fine.
                    }
                }
            )
            jobs.add(
                launch(Dispatchers.IO) {
                    try {
                        val connection = createConnection(
                            proc.inputStream,
                            proc.outputStream,
                            ksrpcEnvironment {
                                errorListener = ErrorListener { t ->
                                    if (t !is CancellationException) {
                                        t.printStackTrace()
                                    }
                                }
                            }
                        )
                        withConnection(connection)
                    } catch (t: CancellationException) {
                        // That's fine.
                    }
                }
            )
            proc.waitFor().also {
                jobs.forEach {
                    try {
                        it.cancel()
                    } catch (t: Throwable) {
                    }
                }
                proc.errorStream.close()
                proc.outputStream.close()
                proc.inputStream.close()
            }
        }
        println("Done executing: $command ($returnCode)")
        return returnCode
    }
}
