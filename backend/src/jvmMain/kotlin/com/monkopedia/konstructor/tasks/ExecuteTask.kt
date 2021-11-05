package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.CompilationStatus.FAILURE
import com.monkopedia.konstructor.common.CompilationStatus.SUCCESS
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.lib.ObjectService
import com.monkopedia.konstructor.lib.TaskState.ERROR
import com.monkopedia.konstructor.lib.TaskStatus
import com.monkopedia.ksrpc.serialized
import java.io.File
import java.lang.StringBuilder

class ExecuteTask(
    private val pkg: String,
    private val fileName: String,
    private val outputDir: File,
    private val config: Config
) : Task {
    override suspend fun execute(): TaskResult {
        val opts = "${config.runtimeOpts}"
        val command = "kotlin $opts -cp ${outputDir.absolutePath} $pkg.$fileName"
        var completed = false
        val errors = StringBuilder()
        var isSuccessful = true
        val taskService = object : ObjectService {
            override suspend fun taskComplete(status: TaskStatus) {
                if (status.state == ERROR) {
                    isSuccessful = false
                }
                status.errors.forEach(errors::append)
            }

            override suspend fun close(u: Unit) {
                completed = true
            }
        }
        val result = ExecUtil.executeWithChannel(command, taskService.serialized(ObjectService))
        return if (completed && isSuccessful && result == 0) {
            TaskResult(
                SUCCESS,
                CompileTask.parseErrors(errors.toString().byteInputStream().bufferedReader())
            )
        } else {
            TaskResult(
                FAILURE,
                CompileTask.parseErrors(errors.toString().byteInputStream().bufferedReader())
            )
        }
    }
}
