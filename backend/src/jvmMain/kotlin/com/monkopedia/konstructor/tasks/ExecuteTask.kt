package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.CompilationStatus.FAILURE
import com.monkopedia.konstructor.common.CompilationStatus.SUCCESS
import com.monkopedia.konstructor.common.TaskResult
import java.io.File

class ExecuteTask(
    private val pkg: String,
    private val fileName: String,
    private val outputDir: File,
    private val config: Config
) : Task {
    override suspend fun execute(): TaskResult {
        val opts = "${config.runtimeOpts}"
        val command = "kotlin $opts -cp ${outputDir.absolutePath} $pkg.$fileName"
        val (stdOut, stdError, result) = ExecUtil.executeAndWait(command)
        return if (result == 0) {
            TaskResult(
                SUCCESS,
                CompileTask.parseErrors(stdOut) + CompileTask.parseErrors(stdError)
            )
        } else {
            TaskResult(
                FAILURE,
                CompileTask.parseErrors(stdOut) + CompileTask.parseErrors(stdError)
            )
        }
    }
}
