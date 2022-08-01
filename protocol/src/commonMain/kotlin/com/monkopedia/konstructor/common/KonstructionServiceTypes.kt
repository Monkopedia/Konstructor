package com.monkopedia.konstructor.common

import kotlinx.serialization.Serializable


enum class CompilationStatus {
    SUCCESS,
    FAILURE
}

@Serializable
data class TaskMessage(
    val message: String,
    val line: Int? = null,
    val char: Int? = null
)

@Serializable
data class TaskResult(
    val status: CompilationStatus,
    val messages: List<TaskMessage> = emptyList()
)