package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.common.CompilationStatus.SUCCESS
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.COMPILING
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.DEFAULT
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.EXECUTING
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.LOADING
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.SAVING
import com.monkopedia.konstructor.frontend.model.ServiceHolder.Companion.tryReconnects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

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
    }.onEach { service ->
        coroutineScope.launch {
            doCompile(service ?: return@launch)
        }
    }.tryReconnects()
        .shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)
    private val reloadTextFlow = MutableSharedFlow<Unit>()

    private val mutableState = MutableStateFlow(LOADING)
    val currentText: Flow<String?> = combine(
        konstructionService,
        reloadTextFlow.onStart { emit(Unit) }
    ) { konstructionService, _ ->
        konstructionService?.fetch().also {
            mutableState.value = DEFAULT
        }
    }.shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)
    val onSave: ((String?) -> Unit) = this::save
    val state: Flow<State> = mutableState
    private val mutableMessages = MutableStateFlow(emptyList<TaskMessage>())
    val messages: Flow<List<TaskMessage>> = mutableMessages
    private val mutableRendered = MutableStateFlow<String?>(null)
    val rendered: Flow<String?> = mutableRendered

    fun save(content: String?) {
        mutableState.value = SAVING
        coroutineScope.launch {
            val service = konstructionService.filterNotNull().first()
            doSave(service, content)
        }
    }

    private suspend fun doSave(
        service: KonstructionService,
        content: String?
    ) {
        service.set(content ?: "")
        coroutineScope.launch {
            reloadTextFlow.emit(Unit)
        }
        doCompile(service)
    }

    private suspend fun doCompile(service: KonstructionService) {
        mutableState.value = COMPILING

        val result = service.compile()
        mutableMessages.value = result.messages

        if (result.status == SUCCESS) {
            doRender(service)
        } else {
            mutableState.value = DEFAULT
            println("Failed compile")
        }
    }

    private suspend fun doRender(service: KonstructionService) {
        mutableState.value = EXECUTING
        val renderResult = service.render()
        mutableMessages.value = mutableMessages.value + renderResult.messages
        if (renderResult.status == SUCCESS) {
            mutableRendered.value = service.rendered()
        } else {
            println("Render failed")
        }
        mutableState.value = DEFAULT
    }

    enum class State {
        LOADING,
        DEFAULT,
        SAVING,
        COMPILING,
        EXECUTING
    }
}
