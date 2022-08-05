package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.CompilationStatus.FAILURE
import com.monkopedia.konstructor.common.CompilationStatus.SUCCESS
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.lib.BuildListener
import com.monkopedia.konstructor.lib.ScriptConfiguration
import com.monkopedia.konstructor.lib.ScriptService
import com.monkopedia.konstructor.lib.ScriptTargetInfo
import com.monkopedia.konstructor.lib.TargetStatus.BUILT
import com.monkopedia.konstructor.lib.TargetStatus.ERROR
import com.monkopedia.ksrpc.toStub
import kotlinx.coroutines.CompletableDeferred
import java.io.File

class ExecuteTask(
    private val pkg: String,
    private val fileName: String,
    private val outputDir: File,
    private val config: Config
) : Task {
    override suspend fun execute(): TaskResult {
        val opts = config.runtimeOpts
        val command = "kotlin $opts -cp ${outputDir.absolutePath} $fileName"
        var completed = false
        val errors = StringBuilder()
        var isSuccessful = true
        val result = ExecUtil.executeWithChannel(command) { connection ->
            val service = connection.defaultChannel().toStub<ScriptService>()
            service.initialize(
                ScriptConfiguration(
                    outputDirectory = outputDir.absolutePath,
                    eagerExport = true
                )
            )
            val exports = service.listTargets(onlyExports = true)
            for (export in exports) {
                val exportService = service.buildTarget(export.name)
                val completion = CompletableDeferred<Unit>()
                exportService.registerListener(object : BuildListener {
                    override suspend fun onStatusUpdated(scriptTargetInfo: ScriptTargetInfo) {
                        when (scriptTargetInfo.status) {
                            BUILT -> completion.complete(Unit)
                            ERROR -> {
                                isSuccessful = false
                                completion.complete(Unit)
                            }
                            else -> {}
                        }
                    }
                })
                completion.await()
                exportService.close()
            }
            completed = true
            try {
                service.closeService(Unit)
            } catch (t: Throwable) {
                // Closing up, thats fin.
            }
            try {
                connection.close()
            } catch (t: Throwable) {
                // Gonna be closed up by the process anyway.
            }
        }
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
