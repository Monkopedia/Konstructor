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
package com.monkopedia.konstructor

import com.monkopedia.hauler.CallSign
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.DirtyState.CLEAN
import com.monkopedia.konstructor.common.DirtyState.NEEDS_COMPILE
import com.monkopedia.konstructor.common.DirtyState.NEEDS_EXEC
import com.monkopedia.konstructor.common.KonstructionCallbacks
import com.monkopedia.konstructor.common.KonstructionCallbacks.CONTENT_CHANGE
import com.monkopedia.konstructor.common.KonstructionCallbacks.DIRTY_CHANGE
import com.monkopedia.konstructor.common.KonstructionCallbacks.INFO_CHANGE
import com.monkopedia.konstructor.common.KonstructionCallbacks.RENDER_CHANGE
import com.monkopedia.konstructor.common.KonstructionCallbacks.TARGET_CHANGE
import com.monkopedia.konstructor.common.KonstructionCallbacks.TASK_COMPLETE
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionListener
import com.monkopedia.konstructor.common.KonstructionRender
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.KonstructionTarget
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.logging.LoggingService
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.logging.callContext
import com.monkopedia.ksrpc.channels.randomUuid
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class KonstructionServiceImpl(
    private val config: Config,
    workspaceId: String,
    id: String,
    private val warehouseWrapper: WarehouseWrapper,
    private val onClose: suspend () -> Unit
) : KonstructionService, LoggingService {
    private val konstructionController = KonstructorManager(config).controllerFor(workspaceId, id)
    private val listenerLock = Mutex()
    private val listeners = mutableMapOf<String, ListenerHandler>()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override val serviceName: String
        get() = "KonstructionService"

    override suspend fun getShipper(u: Unit): Shipper {
        val info = konstructionController.info.konstruction
        return warehouseWrapper.getScoped(
            "${info.workspaceId}.${info.id}",
            info.name
        )
    }

    override suspend fun close() =
        callContext("close", baseCallSign = konstructionController.callSign) {
            super.close()
            job.cancel()
            onClose()
        }

    override suspend fun getName(u: Unit): String =
        callContext("getName", baseCallSign = konstructionController.callSign) {
            konstructionController.info.konstruction.name
        }

    override suspend fun setName(name: String) =
        callContext("setName", baseCallSign = konstructionController.callSign) {
            val info = konstructionController.info
            val newInfo = info.copy(
                konstruction = info.konstruction.copy(name = name)
            )
            konstructionController.info = newInfo
            listenerLock.withLock { listeners.values.toList() }.forEach {
                it.onInfoChanged(newInfo, emptyList())
            }
        }

    override suspend fun getInfo(u: Unit): KonstructionInfo =
        callContext("getInfo", baseCallSign = konstructionController.callSign) {
            konstructionController.info
        }

    override suspend fun fetch(u: Unit): String =
        callContext("fetch", baseCallSign = konstructionController.callSign) {
            konstructionController.read()
        }

    override suspend fun set(content: String) =
        callContext("set", baseCallSign = konstructionController.callSign) {
            konstructionController.write(content)
            val info = konstructionController.info
            var changedTargets = mutableListOf<KonstructionTarget>()
            val newInfo = info.copy(
                dirtyState = NEEDS_COMPILE,
                targets = info.targets.map {
                    if (it.state != NEEDS_COMPILE) {
                        it.copy(state = NEEDS_COMPILE).also(changedTargets::add)
                    } else it
                }
            )
            konstructionController.info = newInfo
            listenerLock.withLock { listeners.values.toList() }.forEach {
                it.onInfoChanged(newInfo, changedTargets, dirtyChanged = false)
                it.onContentChange()
            }
        }

    override suspend fun setBinary(content: ByteReadChannel) =
        callContext("setBinary", baseCallSign = konstructionController.callSign) {
            konstructionController.write(content)
        }

    override suspend fun compile(u: Unit): TaskResult =
        callContext("compile", baseCallSign = konstructionController.callSign) {
            val info = konstructionController.info
            if (info.dirtyState == NEEDS_COMPILE) {
                konstructionController.compile()
                var changedTargets = mutableListOf<KonstructionTarget>()
                val newState =
                    if (konstructionController.lastCompileResult().status == SUCCESS) NEEDS_EXEC
                    else CLEAN
                val newInfo = info.copy(
                    dirtyState = newState,
                    targets = info.targets.map {
                        if (it.state == NEEDS_COMPILE) it.copy(state = newState)
                            .also(changedTargets::add)
                        else it
                    }
                )
                konstructionController.info = newInfo
                listenerLock.withLock { listeners.values.toList() }.forEach {
                    it.onInfoChanged(newInfo, changedTargets)
                }
            }
            konstructionController.lastCompileResult().also { result ->
                listenerLock.withLock { listeners.values.toList() }.forEach {
                    it.onTaskComplete(result)
                }
            }
        }

    override suspend fun register(listener: KonstructionListener): String =
        callContext("register", baseCallSign = konstructionController.callSign) {
            val key = randomUuid()
            val handler = ListenerHandler(key, this, scope, listener).also {
                it.init()
            }
            listenerLock.withLock { listeners[key] = handler }

            key
        }

    override suspend fun unregister(key: String): Boolean =
        callContext("unregister", baseCallSign = konstructionController.callSign) {
            listenerLock.withLock {
                listeners.remove(key)
            }?.onClose() != null
        }

    override suspend fun konstructed(target: String): String? =
        callContext("konstructed", baseCallSign = konstructionController.callSign) {
            konstructionController.renderFile(target)?.toRelativeString(config.dataDir)
                ?.let { "model/$it" }
        }

    override suspend fun requestCompile(u: Unit): Unit =
        callContext("requestCompile", baseCallSign = konstructionController.callSign) {
            scope.launch {
                callContext(
                    "requestCompile.launch",
                    baseCallSign = konstructionController.callSign
                ) {
                    withTimeout(120.seconds) {
                        compile()
                    }
                }
            }
        }

    override suspend fun konstruct(target: String): TaskResult =
        callContext("konstruct", baseCallSign = konstructionController.callSign) {
            konstruct(listOf(target))
        }

    private suspend fun konstruct(targets: List<String>): TaskResult =
        callContext("konstructTargets", baseCallSign = konstructionController.callSign) {
            val info = konstructionController.info
            if (info.dirtyState == NEEDS_EXEC ||
                info.targets.any { it.name in targets && it.state == NEEDS_EXEC }
            ) {
                val allTargets = konstructionController.render(targets)
                val latestBuilt = konstructionController.lastRenderResult().taskArguments
                val existingTargets = info.targets.associateBy { it.name }
                val changedTargets = mutableListOf<KonstructionTarget>()
                val targets = allTargets.map { target ->
                    val dirtyState =
                        if (target in latestBuilt) CLEAN
                        else existingTargets[target]?.state ?: NEEDS_EXEC
                    existingTargets[target]?.let {
                        if (it.state != dirtyState) {
                            it.copy(state = dirtyState).also(changedTargets::add)
                        } else it
                    } ?: KonstructionTarget(target, dirtyState).also(changedTargets::add)
                }
                val newInfo = info.copy(
                    dirtyState = CLEAN,
                    targets = targets
                )
                konstructionController.info = newInfo
                listenerLock.withLock { listeners.values.toList() }.forEach {
                    it.onInfoChanged(newInfo, changedTargets)
                    for (rendered in latestBuilt) {
                        it.onRenderChanged(
                            KonstructionRender(
                                info.konstruction,
                                rendered,
                                konstructed(rendered)
                            )
                        )
                    }
                }
            }
            konstructionController.lastRenderResult().also { result ->
                listenerLock.withLock { listeners.values.toList() }.forEach {
                    it.onTaskComplete(result)
                }
            }
        }

    override suspend fun requestKonstruct(target: String): Unit =
        callContext("requestKonstruct", baseCallSign = konstructionController.callSign) {
            scope.launch {
                callContext(
                    "requestKonstruct.launch",
                    baseCallSign = konstructionController.callSign
                ) {
                    withTimeout(120.seconds) {
                        konstruct(target)
                    }
                }
            }
        }

    override suspend fun requestKonstructs(targets: List<String>): Unit =
        callContext("requestKonstructs", baseCallSign = konstructionController.callSign) {
            scope.launch {
                callContext(
                    "requestKonstructs.launch",
                    baseCallSign = konstructionController.callSign
                ) {
                    withTimeout(120.seconds) {
                        konstruct(targets)
                    }
                }
            }
        }
}

class ListenerHandler(
    private val key: String,
    private val konstruction: KonstructionServiceImpl,
    private val scope: CoroutineScope,
    private val listener: KonstructionListener
) {
    private lateinit var callbacks: Set<KonstructionCallbacks>

    suspend fun init() {
        callbacks = listener.requestedCallbacks().toSet()
    }

    private suspend fun close() {
        konstruction.unregister(key)
    }

    suspend fun onClose() {
        try {
            listener.close()
        } catch (t: Throwable) {
            // Already closing, thats ok.
        }
    }

    suspend fun onInfoChanged(
        info: KonstructionInfo,
        changedTargets: List<KonstructionTarget>,
        dirtyChanged: Boolean = true
    ) {
        if (dirtyChanged) {
            onDirtyStateChanged(info.dirtyState)
        }
        for (target in changedTargets) {
            onTargetChanged(target)
        }
        if (INFO_CHANGE in callbacks) {
            doCallback {
                onInfoChanged(info)
            }
        }
    }

    private suspend fun onDirtyStateChanged(state: DirtyState) {
        if (DIRTY_CHANGE in callbacks) {
            doCallback {
                onDirtyStateChanged(state)
            }
        }
    }

    private suspend fun onTargetChanged(target: KonstructionTarget) {
        if (TARGET_CHANGE in callbacks) {
            doCallback {
                onTargetChanged(target)
            }
        }
    }

    suspend fun onRenderChanged(render: KonstructionRender) {
        if (RENDER_CHANGE in callbacks) {
            doCallback {
                onRenderChanged(render)
            }
        }
    }

    suspend fun onContentChange() {
        if (CONTENT_CHANGE in callbacks) {
            doCallback {
                onContentChange()
            }
        }
    }

    suspend fun onTaskComplete(taskResult: TaskResult) {
        if (TASK_COMPLETE in callbacks) {
            doCallback {
                onTaskComplete(taskResult)
            }
        }
    }

    private suspend inline fun doCallback(
        crossinline function: suspend KonstructionListener.() -> Unit
    ) {
        val callSign = coroutineContext[CallSign.Key]
        scope.launch(callSign ?: EmptyCoroutineContext) {
            withTimeout(120.seconds) {
                try {
                    listener.function()
                } catch (t: Throwable) {
                    hauler("ListenerHandler").error("Exception while calling listener", t)
                    close()
                }
            }
        }
    }
}
