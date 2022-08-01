package com.monkopedia.konstructor.lib

import com.monkopedia.konstructor.lib.TaskState.DONE
import com.monkopedia.konstructor.lib.TaskState.WORKING
import kotlin.system.exitProcess

suspend fun runTasks(args: Array<String>, tasks: List<Task>, service: ObjectService) {
    var completedCount = 0
    for (task in tasks) {
        val (taskInfo, t) = task.executeWithInfo()
        service.taskComplete(
            TaskStatus(
                taskInfo,
                completedCount++,
                tasks.size,
                if (completedCount == tasks.size) DONE else WORKING,
                listOfNotNull(t?.stackTraceToString())
            )
        )
    }
    service.closeService(Unit)
    exitProcess(0)
}

private suspend fun Task.executeWithInfo(): Pair<TaskInfo, Throwable?> {
    val start = System.currentTimeMillis()
    val throwable: Throwable? = try {
        execute()
        null
    } catch (t: Throwable) {
        t
    }
    return TaskInfo(
        name,
        System.currentTimeMillis() - start
    ) to throwable
}

//    override suspend fun execute(u: Unit): TaskStatus {
//        return channel.receive()
//    }
//
//    override suspend fun close(u: Unit) {
//        exitProcess(0)
//    }
// }
