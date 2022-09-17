package com.monkopedia.konstructor

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
import com.monkopedia.ksrpc.channels.randomUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KonstructionServiceImpl(private val config: Config, workspaceId: String, id: String) :
    KonstructionService {
    private val konstructionController = KonstructorManager(config).controllerFor(workspaceId, id)
    private val listenerLock = Mutex()
    private val listeners = mutableMapOf<String, ListenerHandler>()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override suspend fun close() {
        super.close()
        job.cancel()
    }

    override suspend fun getName(u: Unit): String {
        return konstructionController.info.konstruction.name
    }

    override suspend fun setName(name: String) {
        val info = konstructionController.info
        val newInfo = info.copy(
            konstruction = info.konstruction.copy(name = name)
        )
        konstructionController.info = newInfo
        listenerLock.withLock {
            listeners.values.forEach {
                it.onInfoChanged(newInfo, emptyList())
            }
        }
    }

    override suspend fun getInfo(u: Unit): KonstructionInfo {
        return konstructionController.info
    }

    override suspend fun fetch(u: Unit): String {
        return konstructionController.read()
    }

    override suspend fun set(content: String) {
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
        listenerLock.withLock {
            listeners.values.forEach {
                it.onInfoChanged(newInfo, changedTargets, dirtyChanged = false)
                it.onContentChange()
            }
        }
    }

    override suspend fun compile(u: Unit): TaskResult {
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
            listenerLock.withLock {
                listeners.values.forEach {
                    it.onInfoChanged(newInfo, changedTargets)
                }
            }
        }
        return konstructionController.lastCompileResult().also { result ->
            listenerLock.withLock {
                listeners.values.forEach {
                    it.onTaskComplete(result)
                }
            }
        }
    }

    override suspend fun register(listener: KonstructionListener): String {
        val key = randomUuid()
        listenerLock.withLock {
            listeners[key] = ListenerHandler(key, this, scope, listener).also {
                it.init()
            }
        }
        return key
    }

    override suspend fun unregister(key: String): Boolean {
        listenerLock.withLock {
            return listeners.remove(key)?.close() != null
        }
    }

    override suspend fun konstructed(target: String): String? {
        return konstructionController.renderFile(target)?.toRelativeString(config.dataDir)
            ?.let { "model/$it" }
    }

    override suspend fun requestCompile(u: Unit) {
        scope.launch {
            compile()
        }
    }

    override suspend fun konstruct(target: String): TaskResult {
        return konstruct(listOf(target))
    }

    private suspend fun konstruct(targets: List<String>): TaskResult {
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
            listenerLock.withLock {
                listeners.values.forEach {
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
        }
        return konstructionController.lastRenderResult().also { result ->
            listenerLock.withLock {
                listeners.values.forEach {
                    it.onTaskComplete(result)
                }
            }
        }
    }

    override suspend fun requestKonstruct(target: String) {
        scope.launch {
            konstruct(target)
        }
    }

    override suspend fun requestKonstructs(targets: List<String>) {
        scope.launch {
            konstruct(targets)
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

    suspend fun close() {
        konstruction.unregister(key)
    }

    fun onInfoChanged(
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

    private fun onDirtyStateChanged(state: DirtyState) {
        if (DIRTY_CHANGE in callbacks) {
            doCallback {
                onDirtyStateChanged(state)
            }
        }
    }

    private fun onTargetChanged(target: KonstructionTarget) {
        if (TARGET_CHANGE in callbacks) {
            doCallback {
                onTargetChanged(target)
            }
        }
    }

    fun onRenderChanged(render: KonstructionRender) {
        if (RENDER_CHANGE in callbacks) {
            doCallback {
                onRenderChanged(render)
            }
        }
    }

    fun onContentChange() {
        if (CONTENT_CHANGE in callbacks) {
            doCallback {
                onContentChange()
            }
        }
    }

    fun onTaskComplete(taskResult: TaskResult) {
        if (TASK_COMPLETE in callbacks) {
            doCallback {
                onTaskComplete(taskResult)
            }
        }
    }

    private inline fun doCallback(
        crossinline function: suspend KonstructionListener.() -> Unit
    ) {
        scope.launch {
            try {
                listener.function()
            } catch (t: Throwable) {
                t.printStackTrace()
                close()
            }
        }
    }
}
