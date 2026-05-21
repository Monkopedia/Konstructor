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
import com.monkopedia.konstructor.lib.BuildService
import com.monkopedia.konstructor.lib.ScriptService
import com.monkopedia.konstructor.lib.TargetStatus
import com.monkopedia.konstructor.lib.TargetStatus.BUILT
import com.monkopedia.konstructor.lib.TargetStatus.ERROR
import com.monkopedia.konstructor.lib.statusFlow
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.selects.select

class ExecuteTask(
    private val config: Config,
    private val script: ScriptService,
    private val extraTargets: List<String>,
    /**
     * Exit signal for the script subprocess, if the caller has one (see
     * [com.monkopedia.konstructor.hostservices.ScriptManager.getScriptWithExit]).
     *
     * The per-target status wait below parks in `statusFlow().firstOrNull()`, which suspends
     * in the flow's `awaitClose()` until the subprocess pushes a terminal BUILT/ERROR status.
     * If the subprocess hangs and is force-killed, ksrpc wakes pending *calls* but not this
     * parked flow — there's no outstanding call to wake. Racing the wait against this
     * `Deferred` lets the kill surface as a FAILURE promptly instead of hanging until some
     * unrelated timeout. Null callers (e.g. the recursive sub-script path) keep the prior
     * behavior.
     */
    private val subprocessExit: Deferred<Int>? = null
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
            val status = awaitTerminalStatus(export, exportService)

            isSuccessful = status == BUILT
            if (!isSuccessful) {
                hauler.error("Error: ${runCatching { exportService.getErrorTrace() }.getOrNull()}")
            }
            runCatching { exportService.close() }
            hauler.debug("Done with $export")
            if (!isSuccessful && subprocessExit?.isCompleted == true) {
                // The subprocess is gone; remaining targets can't build. Stop here.
                break
            }
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

    /**
     * Wait for [exportService] to reach a terminal BUILT/ERROR status, returning null if the
     * flow ends without one.
     *
     * When [subprocessExit] is supplied, the wait is raced against the subprocess dying: a
     * hung build that gets force-killed completes [subprocessExit] (rather than ever emitting
     * a terminal status), and we report ERROR so [execute] surfaces a FAILURE promptly instead
     * of parking forever in the status flow's `awaitClose()`.
     */
    private suspend fun awaitTerminalStatus(
        export: String,
        exportService: BuildService
    ): TargetStatus? {
        val statusFlow = exportService.statusFlow().onEach {
            hauler.info("Current status $it")
        }.filter { it in listOf(BUILT, ERROR) }
        val exit = subprocessExit ?: return statusFlow.firstOrNull()
        return coroutineScope {
            // The status collector runs as a child of this scope; whichever branch loses the
            // select is cancelled when the scope returns, so no collector is leaked.
            val statusJob = async { statusFlow.firstOrNull() }
            select {
                statusJob.onAwait { it }
                exit.onAwait { exitCode ->
                    statusJob.cancel()
                    hauler.error(
                        "Subprocess exited (code $exitCode) before $export reached a " +
                            "terminal status; reporting build failure"
                    )
                    ERROR
                }
            }
        }
    }
}
