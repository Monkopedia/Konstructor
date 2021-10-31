package com.monkopedia.konstructor.lib

import com.monkopedia.ksrpc.asChannel
import kotlinx.coroutines.runBlocking

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
        val channel = (input to output).asChannel()
        val service = ObjectService.createStub(channel)

        runTasks(args, registry.tasks.toList(), service)
    }
}
