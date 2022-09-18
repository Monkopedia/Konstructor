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
package com.monkopedia.konstructor.lib

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.asConnection
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.ksrpcEnvironment
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.pool.ByteArrayPool
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun runKonstruction(args: Array<String>, script: KcsgScript) {
    runBlocking {
        val input = System.`in`
        val output = System.out
        val channel = (input to output).toChannel()
        channel.registerDefault<ScriptService>(ScriptServiceImpl(script))
        awaitCancellation()
    }
}

suspend fun Pair<InputStream, OutputStream>.toChannel(
    env: KsrpcEnvironment = ksrpcEnvironment { }
): Connection {
    return createConnection(first, second, env)
}

suspend fun createConnection(
    input: InputStream,
    output: OutputStream,
    env: KsrpcEnvironment = ksrpcEnvironment { }
): Connection {
    val channel = ByteChannel(autoFlush = true)
    val threadExecutor = newFixedThreadPoolContext(2, "ServeThreads")
    CoroutineScope(coroutineContext).launch(threadExecutor) {
        channel.copyToAndFlush(output)
        withContext(Dispatchers.IO) {
            threadExecutor.close()
        }
    }
    try {
        return (input.toByteReadChannel(Dispatchers.IO) to channel)
            .asConnection(env)
    } catch (t: Throwable) {
        t.printStackTrace()
        throw t
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
            if (rc == -1 && isClosedForRead) break
            if (rc > 0) {
                out.write(buffer, 0, rc)
                out.flush()
                copied += rc
            }
        }
        try {
            out.close()
        } catch (t: Throwable) {
        }
        Throwable().printStackTrace()

        return copied
    } finally {
        ByteArrayPool.recycle(buffer)
    }
}
