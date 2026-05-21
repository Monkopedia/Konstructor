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
package com.monkopedia.konstructor.hostservices

import com.monkopedia.hauler.CallSign
import com.monkopedia.hauler.debug
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.info
import com.monkopedia.hauler.warn
import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.KonstructorManager
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.lib.ScriptConfiguration
import com.monkopedia.konstructor.lib.ScriptService
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.tasks.ExecUtil
import com.monkopedia.ksrpc.toStub
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ScriptManager private constructor(private val config: Config) {
    private val hauler by lazy { hauler() }

    suspend fun getScript(paths: PathController.Paths, name: String): ScriptService =
        getScriptWithExit(paths, name).first

    /**
     * Like [getScript], but also returns the subprocess exit signal so the caller can race
     * an in-flight build against the child `kotlin` process dying.
     *
     * The render path waits for a build target to reach a terminal status by collecting
     * [com.monkopedia.konstructor.lib.statusFlow], which suspends in `awaitClose()` until the
     * subprocess pushes a BUILT/ERROR callback. When the force-kill timer here destroys a
     * hung subprocess, ksrpc wakes pending *calls* (the receive loop closes the multiChannel),
     * but the status flow has no outstanding call — it's parked in `awaitClose()` — so nothing
     * unblocks it. Handing the exit `Deferred` to [ExecuteTask] lets it race the status wait
     * against process death and surface the kill promptly instead of hanging.
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun getScriptWithExit(
        paths: PathController.Paths,
        name: String
    ): Pair<ScriptService, Deferred<Int>> {
        val opts = config.runtimeOpts
        val command = "kotlin $opts -cp ${paths.compileOutput.absolutePath} ContentKt"
        val scriptHost =
            ScriptHostImpl(this, config, paths.workspaceId, paths.konstructionId, paths.cacheDir)
        val lock = KonstructorManager(config).controllerFor(
            paths.workspaceId,
            paths.konstructionId
        ).scriptLock
        lock.lock()
        hauler.debug("Opening script for ${paths.workspaceId}/${paths.konstructionId}")

        val exec = ExecUtil.executeWithChannel(command)
        val callSign = coroutineContext[CallSign.Key]
        // Release the lock when the subprocess exits — by normal exit or by any
        // of the kill paths below (caller cancellation, init failure, timeout).
        GlobalScope.launch(callSign ?: EmptyCoroutineContext) {
            hauler.info("Waiting for ${paths.workspaceId}/${paths.konstructionId}")
            exec.exitCode.await()
            hauler.info("Exit from ${paths.workspaceId}/${paths.konstructionId}")
            lock.unlock()
        }
        // If the caller is cancelled, reap the subprocess so its exit releases
        // the lock instead of leaking it until the force-kill timeout.
        coroutineContext[Job]?.invokeOnCompletion {
            if (!exec.exitCode.isCompleted) exec.kill()
        }
        try {
            // Bound the setup handshake. Without this, a hang in connect/init
            // holds the lock indefinitely — the force-kill timer below only
            // arms once init has already succeeded.
            val service = withTimeout(config.executeTimeout) {
                val connection = exec.connection.await()
                val service = connection.defaultChannel().toStub<ScriptService, String>()
                service.setShipper(
                    shipper = WarehouseWrapper().getScoped(
                        "${paths.workspaceId}.${paths.konstructionId}",
                        name
                    )
                )
                service.initializeHostServices(scriptHost)
                service.initialize(
                    ScriptConfiguration(
                        outputDirectory = paths.renderOutput.absolutePath,
                        eagerExport = true
                    )
                )
                service
            }
            hauler.debug("Initialized ${paths.workspaceId}/${paths.konstructionId}")
            exec.parentScope.launch(callSign ?: EmptyCoroutineContext) {
                delay(config.executeTimeout)
                hauler.debug("Force killing ${paths.workspaceId}/${paths.konstructionId}")
                exec.kill()
            }
            return service to exec.exitCode
        } catch (t: Throwable) {
            hauler.warn("$name could not be initialized", t)
            // Reap the subprocess so the watcher above releases the lock.
            exec.kill()
            throw InvalidScriptException("$name could not be initialized", t)
        }
    }

    companion object : (Config) -> ScriptManager {
        private val managers = mutableMapOf<Config, ScriptManager>()

        override fun invoke(config: Config): ScriptManager {
            synchronized(managers) {
                return managers.getOrPut(config) {
                    ScriptManager(config)
                }
            }
        }
    }
}
