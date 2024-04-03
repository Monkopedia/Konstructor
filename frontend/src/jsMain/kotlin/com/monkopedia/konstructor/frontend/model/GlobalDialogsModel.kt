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
        }

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
