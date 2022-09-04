package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.utils.MutablePersistentFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
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
        }.onEach {
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
    }

    val onSelectedSpace: ((String) -> Unit) = this::setSelectedSpace
    val onUpdateWorkspaceName: suspend (String) -> Unit = this::setWorkspaceName

    suspend fun setWorkspaceName(name: String) {
    }

    val onCreateWorkspace: suspend (Space) -> Unit = this::createWorkspace

    suspend fun createWorkspace(space: Space) {
    }
}
