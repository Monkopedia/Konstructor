package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.model.ServiceHolder.Companion.tryReconnects
import com.monkopedia.konstructor.frontend.utils.MutablePersistentFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class SpaceListModel(
    private val serviceHolder: ServiceHolder,
    private val coroutineScope: CoroutineScope
) {
    private val relistSpaces = MutableSharedFlow<Unit>()
    private val mutableSelectedSpace = MutablePersistentFlow.optionalString("selected.workspace")

    val availableWorkspaces =
        combine(serviceHolder.service, relistSpaces.onStart { emit(Unit) }) { service, _ ->
            service.list()
        }.tryReconnects()
            .onEach {
                println("Fetch workspaces: $it")
            }.shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)

    fun refreshWorkspaces() {
        coroutineScope.launch {
            relistSpaces.emit(Unit)
        }
    }

    val selectedSpaceId: Flow<String?> = mutableSelectedSpace

    fun setSelectedSpace(spaceId: String) {
        mutableSelectedSpace.set(spaceId)
    }

    val selectedSpace = combine(availableWorkspaces, selectedSpaceId) { workspaces, selectedId ->
        workspaces.find { it.id == selectedId }
    }.tryReconnects()
}
