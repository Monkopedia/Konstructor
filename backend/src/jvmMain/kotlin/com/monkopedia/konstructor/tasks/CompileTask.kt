package com.monkopedia.konstructor.tasks

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.CompilationStatus.FAILURE
import com.monkopedia.konstructor.common.CompilationStatus.SUCCESS
import com.monkopedia.konstructor.common.MessageImportance.ERROR
import com.monkopedia.konstructor.common.MessageImportance.INFO
import com.monkopedia.konstructor.common.MessageImportance.WARNING
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.tasks.ExecUtil.executeAndWait
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.streams.toList
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runInterruptible

class CompileTask(
    private val config: Config,
    private val input: File,
    private val output: File
) : Task {
    override suspend fun execute(): TaskResult = newSingleThreadContext(
        "Compile-${input.absolutePath}"
    ).use { executeThread ->
        runInterruptible(executeThread) {
            val opts = "${config.compilerOpts} ${config.runtimeOpts}"
            val command = "kotlinc $opts -d ${output.absolutePath} ${input.absolutePath}"
            val (stdOut, stdError, result) = executeAndWait(command)
            if (result == 0) {
                TaskResult(
                    SUCCESS,
                    parseErrors(stdOut) + parseErrors(stdError)
                )
            } else {
                TaskResult(
                    FAILURE,
                    parseErrors(stdOut) + parseErrors(stdError)
                )
            }
        }
    }

    companion object {
        private val errorRegex = Regex("^(.*):([0-9]+):([0-9]+): (.*)$")
        private val headerLines = KcsgScript.HEADER.split("\n").size
        private val footerLines = KcsgScript.FOOTER.split("\n").size

        fun parseErrors(stdOut: BufferedReader): List<TaskMessage> {
            return stdOut.lines().filter {
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
