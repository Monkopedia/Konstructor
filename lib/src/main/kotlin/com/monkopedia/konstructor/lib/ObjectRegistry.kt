package com.monkopedia.konstructor.lib

import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedChannel
import com.monkopedia.ksrpc.channels.asConnection
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.toStub
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext

class ObjectRegistry internal constructor() {
    val tasks = mutableSetOf<Task>()
    var isBuilt = false

    fun register(task: Task) {
        require(!isBuilt) {
            "Tasks cannot be added once the object has started building"
        }
        tasks.add(task)
    }
}

interface Task {
    val name: String
    val dependencies: List<Task>
    val isExecuted: Boolean

    suspend fun execute()
}

fun build(args: Array<String>, execMethod: suspend ObjectRegistry.() -> Unit) {
    runBlocking {
        val registry = ObjectRegistry()
        registry.execMethod()
        registry.isBuilt = true

        val input = System.`in`
        val output = System.out
        val channel = (input to output).toChannel()
        val service = channel.defaultChannel().toStub<ObjectService>()
        runTasks(args, registry.tasks.toList(), service)
    }
}

suspend fun Pair<InputStream, OutputStream>.toChannel(): Connection {
    val (input, output) = this
    val channel = ByteChannel(autoFlush = true)
    val job = coroutineContext[Job]
    thread(start = true) {
        channel.toInputStream(job).copyTo(output)
    }
    return (input.toByteReadChannel(Dispatchers.IO) to channel).asConnection(ksrpcEnvironment {  })
}
