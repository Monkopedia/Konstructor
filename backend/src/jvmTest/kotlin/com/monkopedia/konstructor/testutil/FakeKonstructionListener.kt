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

class FakeKonstructionListener(
    private val callbacks: List<KonstructionCallbacks> = KonstructionCallbacks.entries
) : KonstructionListener {
    val infoChanges = mutableListOf<KonstructionInfo>()
    val dirtyChanges = mutableListOf<DirtyState>()
    val targetChanges = mutableListOf<KonstructionTarget>()
    val renderChanges = mutableListOf<KonstructionRender>()
    val contentChanges = mutableListOf<Unit>()
    val taskCompletes = mutableListOf<TaskResult>()

    override suspend fun requestedCallbacks(u: Unit): List<KonstructionCallbacks> = callbacks

    override suspend fun onInfoChanged(info: KonstructionInfo) {
        infoChanges.add(info)
    }

    override suspend fun onDirtyStateChanged(state: DirtyState) {
        dirtyChanges.add(state)
    }

    override suspend fun onTargetChanged(target: KonstructionTarget) {
        targetChanges.add(target)
    }

    override suspend fun onRenderChanged(render: KonstructionRender) {
        renderChanges.add(render)
    }

    override suspend fun onContentChange(u: Unit) {
        contentChanges.add(Unit)
    }

    override suspend fun onTaskComplete(taskResult: TaskResult) {
        taskCompletes.add(taskResult)
    }

    override suspend fun close() {}
}
