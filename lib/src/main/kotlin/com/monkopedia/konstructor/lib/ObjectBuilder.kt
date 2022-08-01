package com.monkopedia.konstructor.lib

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.serialization.Serializable

@Serializable
data class TaskInfo(
    val name: String,
    val elapsedTime: Long
)

enum class TaskState {
    WORKING,
    DONE,
    ERROR
}

@Serializable
data class TaskStatus(
    val taskCompleted: TaskInfo,
    val completedTasks: Int,
    val expectedTasks: Int,
    val state: TaskState,
    val errors: List<String>
)

@KsService
interface ObjectService : RpcService {

    @KsMethod("/report")
    suspend fun taskComplete(status: TaskStatus)

    @KsMethod("/close")
    suspend fun closeService(u: Unit)
}
