package com.monkopedia.konstructor.tasks

import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.SerializedChannel
import com.monkopedia.ksrpc.serve
import io.ktor.util.cio.use
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.pool.ByteArrayPool
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext

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
        )
    }

    suspend fun executeWithChannel(command: String, channel: SerializedChannel): Int {
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
                        channel.doServe(proc.inputStream, proc.outputStream, errorListener = { t ->
                            if (t !is CancellationException) {
                                t.printStackTrace()
                            }
                        })
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
        return returnCode
    }
}

suspend fun SerializedChannel.doServe(
    input: InputStream,
    output: OutputStream,
    errorListener: ErrorListener = ErrorListener { }
) {
    val channel = ByteChannel(autoFlush = true)
    val threadExecutor = newFixedThreadPoolContext(2, "ServeThreads")
    threadExecutor.use {
        channel.use {
            val copy = CoroutineScope(coroutineContext).launch(threadExecutor) {
                channel.copyToAndFlush(output)
            }
            try {
                serve(
                    input.toByteReadChannel(coroutineContext + threadExecutor),
                    this,
                    errorListener = errorListener
                )
            } finally {
                if (!channel.isClosedForWrite) {
                    channel.close()
                }
                copy.join()
            }
        }
    }
}

/**
 * Copies up to [limit] bytes from [this] byte channel to [out] stream suspending on read channel
 * and blocking on output
 *
 * @return number of bytes copied
 */
suspend fun ByteReadChannel.copyToAndFlush(out: OutputStream, limit: Long = Long.MAX_VALUE): Long {
    require(limit >= 0) { "Limit shouldn't be negative: $limit" }

    val buffer = ByteArrayPool.borrow()
    try {
        var copied = 0L
        val bufferSize = buffer.size.toLong()

        while (copied < limit) {
            val rc = readAvailable(buffer, 0, minOf(limit - copied, bufferSize).toInt())
            if (rc == -1) break
            if (rc > 0) {
                out.write(buffer, 0, rc)
                out.flush()
                copied += rc
            }
        }

        return copied
    } finally {
        ByteArrayPool.recycle(buffer)
    }
}
