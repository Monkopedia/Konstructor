package com.monkopedia.konstructor.frontend.koin

import com.monkopedia.konstructor.frontend.model.KonstructionModel
import com.monkopedia.konstructor.frontend.model.SpaceListModel
import com.monkopedia.konstructor.frontend.model.WorkspaceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

class ScopeTracker(
    private val spaceListModel: SpaceListModel,
    private val scope: CoroutineScope
) : KoinComponent {

    private val mutableWorkspace = MutableStateFlow<WorkspaceScope?>(null)
    val workspace = mutableWorkspace.asStateFlow()

    private val targetKonstruction = mutableWorkspace.flatMapLatest { workspaceScope ->
        workspaceScope?.scope?.get<WorkspaceModel>()?.selectedKonstruction?.map {
            workspaceScope to it
        } ?: flowOf(null to null)
    }
    private val mutableKonstruction = MutableStateFlow<KonstructionScope?>(null)
    val konstruction = mutableKonstruction.asStateFlow()

    val currentKonstruction = konstruction.filterNotNull().flatMapLatest {
        it.get<KonstructionModel>().konstruction
    }

    init {
        scope.launch {
            spaceListModel.selectedSpaceId.collect { space ->
                println("Current workspace scope $space")
                mutableWorkspace.value?.closeScope()
                mutableWorkspace.value = space?.let { space -> get { parametersOf(space) } }
            }
        }
        scope.launch {
            targetKonstruction.collectLatest { (parentScope, konstructionId) ->
                println("Current konstruction scope ${parentScope?.get<WorkspaceModel>()?.workspaceId} $konstructionId")
                val lastScope = mutableKonstruction.value
                mutableKonstruction.value = if (parentScope != null && konstructionId != null) {
                    KonstructionScope(parentScope, konstructionId)
                } else null
                launch {
                    // Grace period to avoid react issues.
                    delay(100)
                    lastScope?.closeScope()
                }
            }
        }
    }
}
