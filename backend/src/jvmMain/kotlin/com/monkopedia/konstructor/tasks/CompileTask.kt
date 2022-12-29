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
package com.monkopedia.konstructor.tasks

import com.monkopedia.hauler.CallSign
import com.monkopedia.hauler.asAsync
import com.monkopedia.hauler.debug
import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.warn
import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.MessageImportance.ERROR
import com.monkopedia.konstructor.common.MessageImportance.INFO
import com.monkopedia.konstructor.common.MessageImportance.WARNING
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.tasks.ExecUtil.executeAndWait
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runInterruptible
import java.io.BufferedReader
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

class CompileTask(
    private val config: Config,
    private val input: File,
    private val output: File
) {
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun execute(): TaskResult = newSingleThreadContext(
        "Compile-${input.absolutePath}"
    ).use { executeThread ->
        val callSign = coroutineContext[CallSign.Key]
        runInterruptible(executeThread + (callSign ?: EmptyCoroutineContext)) {
            val opts = "${config.compilerOpts} ${config.runtimeOpts}"
            val command = "kotlinc $opts -d ${output.absolutePath} ${input.absolutePath}"
            val (stdOut, stdError, result) = executeAndWait(command)
            if (result == 0) {
                TaskResult(
                    emptyList(),
                    SUCCESS,
                    parseErrors(stdOut) + parseErrors(stdError, isError = true)
                )
            } else {
                TaskResult(
                    emptyList(),
                    FAILURE,
                    parseErrors(stdOut) + parseErrors(stdError, isError = true)
                )
            }
        }
    }

    companion object {
        @OptIn(DelicateCoroutinesApi::class)
        private val hauler = hauler().asAsync(GlobalScope)
        private val errorRegex = Regex("^(.*):([0-9]+):([0-9]+): (.*)$")
        private val headerLines = KcsgScript.HEADER.split("\n").size
        private val footerLines = KcsgScript.FOOTER.split("\n").size

        fun parseErrors(stdOut: BufferedReader, isError: Boolean = false): List<TaskMessage> {
            return stdOut.lineSequence().onEach {
                if (isError) {
                    hauler.error(it)
                } else {
                    hauler.debug(it)
                }
            }.filter {
                it.startsWith("error:") || it.startsWith("warning:") || errorRegex.matches(it)
            }.map {
                if (it.startsWith("error:")) {
                    TaskMessage(it, importance = ERROR)
                } else if (it.startsWith("warning:")) {
                    TaskMessage(it, importance = WARNING)
                } else {
                    val match = errorRegex.matchEntire(it)
                        ?: error("Confused about how to parse $it")
                    val line = match.groupValues[2].toInt()
                    val message = match.groupValues[4]
                    if (line >= headerLines) {
                        val char = match.groupValues[3].toInt()
                        TaskMessage(
                            message = message,
                            line = line - headerLines,
                            char = char,
                            importance = when {
                                message.startsWith("warning") -> WARNING
                                message.startsWith("info") -> INFO
                                else -> ERROR
                            }
                        )
                    } else {
                        TaskMessage(
                            message = "Internal error: $message",
                            line = 0,
                            char = 0
                        )
                    }
                }
            }.toList()
        }
    }
}
