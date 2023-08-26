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

import com.monkopedia.hauler.debug
import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.info
import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.lib.ScriptService
import com.monkopedia.konstructor.lib.TargetStatus.BUILT
import com.monkopedia.konstructor.lib.TargetStatus.ERROR
import com.monkopedia.konstructor.lib.statusFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach

class ExecuteTask(
    private val config: Config,
    private val script: ScriptService,
    private val extraTargets: List<String>
) {
    private val hauler by lazy { hauler() }
    suspend fun execute(): Pair<TaskResult, List<String>> {
        val errors = StringBuilder()
        var isSuccessful = true
        val exports = script.listTargets(onlyExports = true)
        val allTargets = script.listTargets(onlyExports = false).map { it.name }
        val validExtraTargets = extraTargets.filter { it in allTargets }
        val builtTargets = (exports.map { it.name } + validExtraTargets).distinct()
        for (export in builtTargets) {
            hauler.debug("Starting building $export")
            val exportService = script.buildTarget(export)
            val status = exportService.statusFlow().onEach {
                hauler.info("Current status $it")
            }.filter { it in listOf(BUILT, ERROR) }.firstOrNull()

            isSuccessful = status == BUILT
            if (!isSuccessful) {
                hauler.error("Error: ${exportService.getErrorTrace()}")
            }
            exportService.close()
            hauler.debug("Done with $export")
        }
        try {
            script.closeService(Unit)
        } catch (t: Throwable) {
            // Closing up, thats fin.
        }
        try {
            script.close()
        } catch (t: Throwable) {
            // Closing up, thats fin.
        }
        return if (isSuccessful) {
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
