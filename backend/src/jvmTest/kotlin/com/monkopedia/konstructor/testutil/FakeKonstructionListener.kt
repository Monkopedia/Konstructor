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
package com.monkopedia.konstructor.testutil

import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.KonstructionCallbacks
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionListener
import com.monkopedia.konstructor.common.KonstructionRender
import com.monkopedia.konstructor.common.KonstructionTarget
import com.monkopedia.konstructor.common.TaskResult
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

/**
 * Records every callback the server delivers, in arrival order, so tests can
 * assert call counts, ordering, and the thread each callback ran on.
 *
 * All recording collections are thread-safe because the production
 * [com.monkopedia.konstructor.ListenerHandler] dispatches each callback on its
 * own `scope.launch`, so concurrent callbacks may land on different threads.
 *
 * Behaviour can be customised per instance:
 *  - [throwOn] makes the matching callback throw, exercising the
 *    catch/close/unregister path.
 *  - [blockUntil] makes the matching callback suspend until the deferred is
 *    completed, used to model a slow or dead client.
 */
class FakeKonstructionListener(
    private val callbacks: List<KonstructionCallbacks> = KonstructionCallbacks.entries,
    private val throwOn: KonstructionCallbacks? = null,
    private val blockUntil: Map<KonstructionCallbacks, CompletableDeferred<Unit>> = emptyMap()
) : KonstructionListener {
    val infoChanges: MutableList<KonstructionInfo> = Collections.synchronizedList(mutableListOf())
    val dirtyChanges: MutableList<DirtyState> = Collections.synchronizedList(mutableListOf())
    val targetChanges: MutableList<KonstructionTarget> =
        Collections.synchronizedList(mutableListOf())
    val renderChanges: MutableList<KonstructionRender> =
        Collections.synchronizedList(mutableListOf())
    val contentChanges: MutableList<Unit> = Collections.synchronizedList(mutableListOf())
    val taskCompletes: MutableList<TaskResult> = Collections.synchronizedList(mutableListOf())

    /** Names of callbacks in the order they were received (across all callback types). */
    val callOrder: MutableList<KonstructionCallbacks> =
        Collections.synchronizedList(mutableListOf())

    /** Names of threads each callback executed on. */
    val callThreads: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Total callbacks delivered, regardless of type. */
    val totalCalls: AtomicInteger = AtomicInteger(0)

    /** True once [close] has been called by the server (e.g. after a throwing callback). */
    @Volatile
    var closed: Boolean = false
        private set

    private fun record(type: KonstructionCallbacks) {
        callOrder.add(type)
        callThreads.add(Thread.currentThread().name)
        totalCalls.incrementAndGet()
    }

    private suspend fun maybeBlock(type: KonstructionCallbacks) {
        blockUntil[type]?.await()
    }

    private fun maybeThrow(type: KonstructionCallbacks) {
        if (type == throwOn) {
            throw RuntimeException("simulated listener failure on $type")
        }
    }

    override suspend fun requestedCallbacks(u: Unit): List<KonstructionCallbacks> = callbacks

    override suspend fun onInfoChanged(info: KonstructionInfo) {
        record(KonstructionCallbacks.INFO_CHANGE)
        infoChanges.add(info)
        maybeBlock(KonstructionCallbacks.INFO_CHANGE)
        maybeThrow(KonstructionCallbacks.INFO_CHANGE)
    }

    override suspend fun onDirtyStateChanged(state: DirtyState) {
        record(KonstructionCallbacks.DIRTY_CHANGE)
        dirtyChanges.add(state)
        maybeBlock(KonstructionCallbacks.DIRTY_CHANGE)
        maybeThrow(KonstructionCallbacks.DIRTY_CHANGE)
    }

    override suspend fun onTargetChanged(target: KonstructionTarget) {
        record(KonstructionCallbacks.TARGET_CHANGE)
        targetChanges.add(target)
        maybeBlock(KonstructionCallbacks.TARGET_CHANGE)
        maybeThrow(KonstructionCallbacks.TARGET_CHANGE)
    }

    override suspend fun onRenderChanged(render: KonstructionRender) {
        record(KonstructionCallbacks.RENDER_CHANGE)
        renderChanges.add(render)
        maybeBlock(KonstructionCallbacks.RENDER_CHANGE)
        maybeThrow(KonstructionCallbacks.RENDER_CHANGE)
    }

    override suspend fun onContentChange(u: Unit) {
        record(KonstructionCallbacks.CONTENT_CHANGE)
        contentChanges.add(Unit)
        maybeBlock(KonstructionCallbacks.CONTENT_CHANGE)
        maybeThrow(KonstructionCallbacks.CONTENT_CHANGE)
    }

    override suspend fun onTaskComplete(taskResult: TaskResult) {
        record(KonstructionCallbacks.TASK_COMPLETE)
        taskCompletes.add(taskResult)
        maybeBlock(KonstructionCallbacks.TASK_COMPLETE)
        maybeThrow(KonstructionCallbacks.TASK_COMPLETE)
    }

    override suspend fun close() {
        closed = true
    }
}
