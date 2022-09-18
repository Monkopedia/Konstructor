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
package com.monkopedia.konstructor.common

import com.monkopedia.konstructor.common.MessageImportance.ERROR
import kotlinx.serialization.Serializable

enum class TaskStatus {
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
    val importance: MessageImportance = ERROR
)

@Serializable
data class TaskResult(
    val taskArguments: List<String> = emptyList(),
    val status: TaskStatus,
    val messages: List<TaskMessage> = emptyList()
)
