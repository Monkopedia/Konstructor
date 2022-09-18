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

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.lib.BuildListener
import com.monkopedia.konstructor.lib.ScriptConfiguration
import com.monkopedia.konstructor.lib.ScriptService
import com.monkopedia.konstructor.lib.ScriptTargetInfo
import com.monkopedia.konstructor.lib.TargetStatus.BUILT
import com.monkopedia.konstructor.lib.TargetStatus.ERROR
import com.monkopedia.ksrpc.toStub
import java.io.File
import kotlinx.coroutines.CompletableDeferred

class ExecuteTask(
    private val fileName: String,
    private val outputDir: File,
    private val renderOutputDir: File = outputDir,
    private val config: Config,
    private val extraTargets: List<String>
) {
    suspend fun execute(): Pair<TaskResult, List<String>> {
        val opts = config.runtimeOpts
        val command = "kotlin $opts -cp ${outputDir.absolutePath} $fileName"
        var completed = false
        val errors = StringBuilder()
        var isSuccessful = true
        var allTargets = emptyList<String>()
        var builtTargets = emptyList<String>()
        val result = ExecUtil.executeWithChannel(command) { connection ->
            val service = connection.defaultChannel().toStub<ScriptService>()
            service.initialize(
                ScriptConfiguration(
                    outputDirectory = renderOutputDir.absolutePath,
                    eagerExport = true
                )
            )
            val exports = service.listTargets(onlyExports = true)
            allTargets = service.listTargets(onlyExports = false).map { it.name }
            val validExtraTargets = extraTargets.filter { it in allTargets }
            builtTargets = (exports.map { it.name } + validExtraTargets).distinct()
            for (export in builtTargets) {
                val exportService = service.buildTarget(export)
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
                builtTargets,
                SUCCESS,
                CompileTask.parseErrors(errors.toString().byteInputStream().bufferedReader())
            ) to allTargets
        } else {
            TaskResult(
                builtTargets,
                FAILURE,
                CompileTask.parseErrors(errors.toString().byteInputStream().bufferedReader())
            ) to allTargets
        }
    }
}
