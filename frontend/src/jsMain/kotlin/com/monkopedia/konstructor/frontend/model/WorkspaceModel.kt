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

import com.monkopedia.konstructor.frontend.model.ServiceHolder.Companion.tryReconnects
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

    private val mutableSelectedKonstruction =
        MutablePersistentFlow.optionalString("selected.konstruction")
    private val service = serviceHolder.service.map {
        println("Getting service for $workspaceId")
        it.get(workspaceId)
    }.tryReconnects()
        .shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)

    val availableKonstructions =
        combine(service, relistKonstructions.onStart { emit(Unit) }) { service, _ ->
            println("Getting konstructions for $workspaceId")
            service.list()
        }.tryReconnects()
            .shareIn(coroutineScope, SharingStarted.Lazily, replay = 1)

    fun refreshKonstructions() {
        coroutineScope.launch {
            refreshAllKonstructions()
        }
    }

    val selectedKonstruction: Flow<String?> = mutableSelectedKonstruction

    fun setSelectedKonstruction(konstructionId: String) {
        mutableSelectedKonstruction.set(konstructionId)
    }

    companion object {
        private val relistKonstructions = MutableSharedFlow<Unit>()

        suspend fun refreshAllKonstructions() {
            relistKonstructions.emit(Unit)
        }
    }
}
