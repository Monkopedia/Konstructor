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
import com.monkopedia.konstructor.lsp.BridgeLanguageServer
import com.monkopedia.lsp.KsrpcLanguageClient
import com.monkopedia.lsp.KsrpcLanguageServer
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Upper bound on a single compile/konstruct/listener-callback before it's cancelled. */
private val callTimeout = 120.seconds

class KonstructionServiceImpl(
    private val config: Config,
    private val workspaceId: String,
    private val id: String,
    private val warehouseWrapper: WarehouseWrapper,
    private val onClose: suspend () -> Unit
) : KonstructionService,
    LoggingService {
    private val konstructionController = KonstructorManager(config).controllerFor(workspaceId, id)
    private val listenerLock = Mutex()
    private val listeners = mutableMapOf<String, ListenerHandler>()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override val serviceName: String
        get() = "KonstructionService"

    /**
     * Snapshots the current listeners under [listenerLock] and invokes [block] on each.
     *
     * The `.toList()` under the lock is load-bearing: it copies the handler set before
     * iterating so the (suspending) callbacks never run while holding the lock and can't
     * hit a concurrent modification of [listeners].
     */
    private suspend inline fun broadcast(block: ListenerHandler.() -> Unit) {
        listenerLock.withLock { listeners.values.toList() }.forEach { it.block() }
    }

    /**
     * Fire-and-forget shell shared by the `request*` methods: launches [block] on [scope]
     * inside a named [callContext], bounded by [callTimeout].
     */
    private fun launchRequest(name: String, block: suspend () -> Unit) {
        scope.launch {
            callContext("$name.launch", baseCallSign = konstructionController.callSign) {
                withTimeout(callTimeout) {
                    block()
                }
            }
        }
    }

    override suspend fun getShipper(u: Unit): Shipper {
        val info = konstructionController.info.konstruction
        return warehouseWrapper.getScoped(
            "${info.workspaceId}.${info.id}",
            info.name
        )
    }

    override suspend fun lsp(client: KsrpcLanguageClient): KsrpcLanguageServer =
        callContext("lsp", baseCallSign = konstructionController.callSign) {
            // Phase 2 (epic #35): a real bridge from this nested-ksrpc leg to a warm
            // JetBrains kotlin-lsp subprocess. The editor's [client] (passed as a ksrpc
            // param, so its publishDiagnostics channel rides the same WebSocket) is
            // stashed and the bridge forwards real kcsg-aware diagnostics to it.
            //
            // Still wholly flag-gated on the frontend (lspEnabled, default OFF) and the
            // bridge degrades to an inert server when no kotlin-lsp binary is configured
            // (CI), so the absence of the engine never crashes konstructor.
            BridgeLanguageServer(config, workspaceId, id, client)
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
            broadcast {
                onInfoChanged(newInfo, emptyList())
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
                    } else {
                        it
                    }
                }
            )
            konstructionController.info = newInfo
            broadcast {
                onInfoChanged(newInfo, changedTargets, dirtyChanged = false)
                onContentChange()
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
                    if (konstructionController.lastCompileResult().status == SUCCESS) {
                        NEEDS_EXEC
                    } else {
                        CLEAN
                    }
                val newInfo = info.copy(
                    dirtyState = newState,
                    targets = info.targets.map {
                        if (it.state == NEEDS_COMPILE) {
                            it.copy(state = newState)
                                .also(changedTargets::add)
                        } else {
                            it
                        }
                    }
                )
                konstructionController.info = newInfo
                broadcast {
                    onInfoChanged(newInfo, changedTargets)
                }
            }
            konstructionController.lastCompileResult().also { result ->
                broadcast {
                    onTaskComplete(result)
                }
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun register(listener: KonstructionListener): String =
        callContext("register", baseCallSign = konstructionController.callSign) {
            val key = Uuid.random().toString()
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
            launchRequest("requestCompile") { compile() }
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
                        if (target in latestBuilt) {
                            CLEAN
                        } else {
                            existingTargets[target]?.state ?: NEEDS_EXEC
                        }
                    existingTargets[target]?.let {
                        if (it.state != dirtyState) {
                            it.copy(state = dirtyState).also(changedTargets::add)
                        } else {
                            it
                        }
                    } ?: KonstructionTarget(target, dirtyState).also(changedTargets::add)
                }
                val newInfo = info.copy(
                    dirtyState = CLEAN,
                    targets = targets
                )
                konstructionController.info = newInfo
                broadcast {
                    onInfoChanged(newInfo, changedTargets)
                    for (rendered in latestBuilt) {
                        onRenderChanged(
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
                broadcast {
                    onTaskComplete(result)
                }
            }
        }

    override suspend fun requestKonstruct(target: String): Unit =
        callContext("requestKonstruct", baseCallSign = konstructionController.callSign) {
            launchRequest("requestKonstruct") { konstruct(target) }
        }

    override suspend fun requestKonstructs(targets: List<String>): Unit =
        callContext("requestKonstructs", baseCallSign = konstructionController.callSign) {
            launchRequest("requestKonstructs") { konstruct(targets) }
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
            try {
                withTimeout(callTimeout) {
                    listener.function()
                }
            } catch (t: Throwable) {
                hauler("ListenerHandler").error("Exception while calling listener", t)
                // Cleanup must run outside the (now-cancelled) timeout scope:
                // close() suspends, and inside a cancelling coroutine it would
                // immediately throw CancellationException and never actually
                // unregister — leaking the dead listener (the 120s-flood bug).
                withContext(NonCancellable) { close() }
            }
        }
    }
}
