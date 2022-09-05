package com.monkopedia.konstructor.common

import com.monkopedia.konstructor.common.MessageImportance.ERROR
import kotlinx.serialization.Serializable


enum class CompilationStatus {
    SUCCESS,
    FAILURE
}

enum class MessageImportance {
    ERROR,
    WARNING,
    INFO
}

@Serializable
data class TaskMessage(
    val message: String,
    val line: Int? = null,
    val char: Int? = null,
    val importance: MessageImportance = ERROR,
)

@Serializable
data class TaskResult(
    val status: CompilationStatus,
    val messages: List<TaskMessage> = emptyList()
)