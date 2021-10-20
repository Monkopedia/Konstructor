package com.monkopedia.konstructor.common

import com.monkopedia.ksrpc.KsMethod
import com.monkopedia.ksrpc.KsService
import com.monkopedia.ksrpc.RpcService
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable

enum class CompilationStatus {
    SUCCESS,
    FAILURE
}

@Serializable
data class TaskMessage(
    val message: String,
    val line: Int? = null
)

@Serializable
data class TaskResult(
    val status: CompilationStatus,
    val messages: List<TaskMessage> = emptyList()
)

@KsService
interface KonstructionService : RpcService {
    @KsMethod("/name")
    suspend fun getName(u: Unit): String

    @KsMethod("/set_name")
    suspend fun setName(name: String)

    @KsMethod("/fetch")
    suspend fun fetch(u: Unit): ByteReadChannel

    @KsMethod("/set")
    suspend fun set(content: ByteReadChannel)

    @KsMethod("/compile")
    suspend fun compile(u: Unit): TaskResult

    @KsMethod("/rendered")
    suspend fun rendered(u: Unit): String?

    @KsMethod("/render")
    suspend fun render(u: Unit): TaskResult
}
