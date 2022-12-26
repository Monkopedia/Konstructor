package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.koin.ScopeTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.get

class GlobalDialogsModel(
    coroutineScope: CoroutineScope,
    val workManager: WorkManager,
    scopeTracker: ScopeTracker
) {
    private val konstructionModel = scopeTracker.konstruction.map { it?.get<KonstructionModel>() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)
    val hasConflictingState: Flow<Boolean> =
        scopeTracker.konstruction.filterNotNull().flatMapLatest {
            it.get<KonstructionModel>().pendingText
        }.map { it != null }

    fun overwriteState() {
        val model = konstructionModel.value ?: return
        workManager.doWork {
            model.save()
        }
    }

    fun discardState() {
        val model = konstructionModel.value ?: return
        workManager.doWork {
            model.discardLocalChanges()
        }
    }
}
