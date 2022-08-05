package com.monkopedia.konstructor.lib

import com.monkopedia.konstructor.lib.LoggingLevel.DEFAULT
import kotlinx.serialization.Serializable

enum class LoggingLevel {
    DEFAULT
}

@Serializable
data class ScriptConfiguration(
    val loggingLevel: LoggingLevel = DEFAULT,
    val outputDirectory: String,
    val eagerExport: Boolean = false
)

enum class TargetStatus {
    NONE,
    BUILDING,
    BUILT,
    ERROR
}

@Serializable
data class ScriptTargetInfo(
    val name: String,
    val status: TargetStatus
)