package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.frontend.utils.MutablePersistentFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class WorkspaceModel(
    private val serviceHolder: ServiceHolder,
    val workspaceId: String,
    private val coroutineScope: CoroutineScope
) {

    private val relistKonstructions = MutableSharedFlow<Unit>()
    private val mutableSelectedKonstruction =
        MutablePersistentFlow.optionalString("selected.konstruction")
    private val service = serviceHolder.service.map {
        it.get(workspaceId)
    }.shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)

    val availableKonstructions =
        combine(service, relistKonstructions.onStart { emit(Unit) }) { service, _ ->
            service.list()
        }.shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)

    fun refreshKonstructions() {
        coroutineScope.launch {
            relistKonstructions.emit(Unit)
        }
    }

    val selectedKonstruction: Flow<String?> = mutableSelectedKonstruction

    fun setSelectedKonstruction(konstructionId: String) {
        mutableSelectedKonstruction.set(konstructionId)
    }
    val onSelectedKonstruction: ((String) -> Unit) = this::setSelectedKonstruction

    val currentKonstruction: Flow<Konstruction?> =
        combine(availableKonstructions, selectedKonstruction) { konstructions, selected ->
            konstructions.find { it.id == selected }
        }.shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)
    val onNameUpdated: suspend (String) -> Unit = this::setName

    suspend fun setName(value: String) {
    }
    val onCreateKonstruction: suspend (Konstruction) -> Unit = this::createKonstruction

    suspend fun createKonstruction(konstruction: Konstruction) {

    }
}
