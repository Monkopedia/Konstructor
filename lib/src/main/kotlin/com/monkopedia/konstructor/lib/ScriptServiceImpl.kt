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
package com.monkopedia.konstructor.lib

import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.attach
import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.lib.TargetStatus.NONE
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ScriptServiceImpl(private val script: KcsgScript, private val exec: KcsgScript.() -> Unit) : ScriptService {
    private var shipper: Shipper? = null
    private val serviceCache = mutableMapOf<String, BuildServiceImpl>()
    private val statusCache = mutableMapOf<String, TargetStatus>()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    private val dispatchThread = BlockableThread("SingleThreadScript").also { dispatcher ->
        job.invokeOnCompletion {
            dispatcher.close()
        }
    }
    private val lock = Mutex()
    private lateinit var outputDirectory: File
    private var isInitialized = false

    override suspend fun initializeHostServices(hostService: HostService) {
        script.host = KcsgRemoteHostImpl(hostService, dispatchThread)
    }

    override suspend fun initialize(config: ScriptConfiguration) {
        if (isInitialized) {
            throw IllegalArgumentException("Already initialized")
        }
        outputDirectory = File(config.outputDirectory)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IllegalArgumentException("Can't write to ${outputDirectory.absolutePath}")
        }
        shipper?.requestDockPickup()?.attach(scope, config.loggingDeliveryRates.asDeliveryRates)
        lock.withLock {
            script.exec()
        }
        isInitialized = true
        if (config.eagerExport) {
            for (target in listTargets(onlyExports = true)) {
                buildTarget(target.name)
            }
        }
    }

    override suspend fun setShipper(shipper: Shipper) {
        this.shipper = shipper
    }

    override suspend fun listTargets(onlyExports: Boolean): List<ScriptTargetInfo> {
        val targets = withContext(dispatchThread.dispatcher) {
            if (onlyExports) script.exports() else script.targets()
        }
        lock.withLock {
            return targets.map { target ->
                ScriptTargetInfo(
                    target,
                    serviceCache[target]?.getStatus() ?: statusCache[target] ?: NONE
                )
            }
        }
    }

    override suspend fun buildTarget(name: String): BuildService {
        lock.withLock {
            return serviceCache.getOrPut(name) {
                BuildServiceImpl(
                    scope,
                    dispatchThread.dispatcher,
                    script,
                    name,
                    outputDirectory
                ) { status ->
                    lock.withLock {
                        statusCache[name] = status
                        serviceCache.remove(name)
                    }
                }
            }
        }
    }

    override suspend fun closeService(u: Unit) {
        try {
            job.cancel()
        } finally {
            exitProcess(0)
        }
    }

    override suspend fun close() = closeService()
}
