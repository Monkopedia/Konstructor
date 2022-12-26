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
package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.DirtyState.CLEAN
import com.monkopedia.konstructor.common.DirtyState.NEEDS_COMPILE
import com.monkopedia.konstructor.common.DirtyState.NEEDS_EXEC
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionCallbacks
import com.monkopedia.konstructor.common.KonstructionCallbacks.CONTENT_CHANGE
import com.monkopedia.konstructor.common.KonstructionCallbacks.INFO_CHANGE
import com.monkopedia.konstructor.common.KonstructionCallbacks.RENDER_CHANGE
import com.monkopedia.konstructor.common.KonstructionCallbacks.TASK_COMPLETE
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionListener
import com.monkopedia.konstructor.common.KonstructionRender
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.KonstructionTarget
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.frontend.editor.asString
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.COMPILING
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.DEFAULT
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.EXECUTING
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.LOADING
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.SAVING
import com.monkopedia.konstructor.frontend.model.ServiceHolder.Companion.tryReconnects
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.Transaction
import dukat.codemirror.view.EditorView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class KonstructionModel(
    private val serviceHolder: ServiceHolder,
    private val workspaceModel: WorkspaceModel,
    val konstructionId: String,
    private val coroutineScope: CoroutineScope
) {
    val workspaceId: String
        get() = workspaceModel.workspaceId

    val konstruction: Flow<Konstruction?> =
        workspaceModel.availableKonstructions.map { konstructions ->
            konstructions.find { it.id == konstructionId }
        }.shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)
    private val konstructionService: Flow<KonstructionService?> = combine(
        serviceHolder.service,
        konstruction
    ) { service, konstruction ->
        konstruction?.let { service.konstruction(it) }
    }.tryReconnects()
        .shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)
    private val reloadTextFlow = MutableSharedFlow<Unit>()

    private val mutableState = MutableStateFlow(LOADING)
    val currentText: Flow<String?> = combine(
        konstructionService,
        reloadTextFlow.onStart { emit(Unit) }
    ) { konstructionService, _ ->
        konstructionService?.fetch().also {
            if (mutableState.value == LOADING) {
                mutableState.value = DEFAULT
            }
        }
    }.shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)

    private val mutableInfo = MutableStateFlow<KonstructionInfo?>(null)
    val info: Flow<KonstructionInfo> = mutableInfo.filterNotNull()

    val onSave: ((String?) -> Unit) = this::save
    val state: Flow<State> = mutableState

    private val mutableMessages = MutableStateFlow(emptyList<TaskMessage>())
    val messages: Flow<List<TaskMessage>> = mutableMessages

    private val mutableRendered = MutableStateFlow<Map<String, String>>(emptyMap())
    val rendered: Flow<Map<String, String>> = mutableRendered

    private val mutableReload = MutableStateFlow(0)
    val reload: Flow<Int> = mutableReload

    private val mutableWatchingTargets = MutableStateFlow<List<String>>(emptyList())
    val watchingTargets: Flow<List<String>> = mutableWatchingTargets

    private val konstructorEditorState by lazy {
        KonstructorEditorState(this, coroutineScope)
    }
    val editorState: EditorState
        get() = konstructorEditorState.editorState
    val currentMessage: Flow<String?>
        get() = konstructorEditorState.currentMessage
    val setView: (EditorView?) -> Unit
        get() = konstructorEditorState.setView
    val pendingText: MutableStateFlow<String?>
        get() = konstructorEditorState.pendingText

    init {
        coroutineScope.launch {
            konstructionService.flatMapLatest {
                it?.listener() ?: emptyFlow()
            }.collect()
        }
        coroutineScope.launch {
            konstructionService.filterNotNull().collect { service ->
                mutableInfo.value = service.getInfo()
                service.requestCompile()
                service.requestKonstructs(emptyList())
            }
        }
        coroutineScope.launch {
            combine(konstructionService.filterNotNull(), watchingTargets, info, ::Triple)
                .collect { (service, watchingTargets, info) ->
                    if (info.dirtyState == NEEDS_COMPILE) {
                        mutableState.value = COMPILING
                        service.requestCompile()
                    }
                    val targets = info.targets.associateBy { it.name }
                    val unrenderedTargets = watchingTargets.filter {
                        targets[it]?.state == NEEDS_EXEC
                    }

                    if (unrenderedTargets.isNotEmpty() || info.dirtyState == NEEDS_EXEC) {
                        mutableState.value = EXECUTING
                        service.requestKonstructs(unrenderedTargets)
                    } else if (info.dirtyState == CLEAN) {
                        mutableState.value = DEFAULT
                    }
                    mutableRendered.value = mutableRendered.value.filterKeys {
                        it in targets.keys
                    }
                    watchingTargets.filter {
                        targets[it]?.state == CLEAN
                    }.forEach { target ->
                        val konstructed = service.konstructed(target) ?: return@forEach
                        mutableRendered.value = mutableRendered.value.toMutableMap().apply {
                            this[target] = konstructed
                        }
                    }
                }
        }
    }

    fun setTargets(targets: List<String>) {
        mutableWatchingTargets.value = targets
    }

    suspend fun save() {
        val service = konstructionService.filterNotNull().first()
        val newText = konstructorEditorState.save()
        doSave(service, newText)
    }

    suspend fun discardLocalChanges() {
        konstructorEditorState.discardLocalChanges()
    }

    fun save(content: String?) {
        mutableState.value = SAVING
        coroutineScope.launch {
            val service = konstructionService.filterNotNull().first()
            doSave(service, content)
        }
    }

    private suspend fun doSave(service: KonstructionService, content: String?) {
        service.set(content ?: "")
    }

    enum class State {
        LOADING,
        DEFAULT,
        SAVING,
        COMPILING,
        EXECUTING
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun KonstructionService.listener(): Flow<Unit> {
        return callbackFlow {
            val listener = object : KonstructionListener {
                override suspend fun requestedCallbacks(u: Unit): List<KonstructionCallbacks> =
                    listOf(
                        CONTENT_CHANGE,
                        INFO_CHANGE,
                        RENDER_CHANGE,
                        TASK_COMPLETE
                    )

                override suspend fun onInfoChanged(info: KonstructionInfo) {
//                    println("onInfoChanged $info")
                    mutableInfo.value = info
                }

                override suspend fun onDirtyStateChanged(state: DirtyState) = Unit

                override suspend fun onTargetChanged(target: KonstructionTarget) = Unit

                override suspend fun onRenderChanged(render: KonstructionRender) {
//                    println("onRenderChanged $render")
                    val renderPath = render.renderPath
                    mutableRendered.value = mutableRendered.value.toMutableMap().apply {
                        if (renderPath != null) {
                            this[render.name] = renderPath
                        } else {
                            this.remove(render.name)
                        }
                    }
                    mutableReload.value += 1
                }

                override suspend fun onContentChange(u: Unit) {
//                    println("onContentChange")
                    mutableMessages.value = emptyList()
                    coroutineScope.launch {
                        reloadTextFlow.emit(Unit)
                    }
                }

                override suspend fun onTaskComplete(taskResult: TaskResult) {
//                    println("onTaskComplete $taskResult")
                    mutableMessages.value += taskResult.messages
                }
            }
            val key = register(listener)
            send(Unit)
            awaitClose {
                GlobalScope.launch {
                    try {
                        unregister(key)
                    } catch (t: Throwable) {
                    }
                }
            }
        }
    }
}
