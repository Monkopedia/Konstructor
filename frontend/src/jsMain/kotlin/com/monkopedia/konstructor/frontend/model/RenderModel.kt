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

import com.monkopedia.konstructor.frontend.utils.MutablePersistentFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class RenderModel(
    private val coroutineScope: CoroutineScope,
    private val konstructionModel: KonstructionModel
) {
    @Serializable
    data class DisplayTarget(
        val name: String,
        val color: String,
        val isEnabled: Boolean
    )

    private val numberOfTargets = MutablePersistentFlow.serialized(
        "rendered.${konstructionModel.workspaceId}.${konstructionModel.konstructionId}.keys",
        emptySet<String>()
    )
    private val targetFlows = numberOfTargets.map { names ->
        names.associateWith { name ->
            persistentFlowFor(name)
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    val allTargets = targetFlows.flatMapLatest { map ->
        combine(map.values) { values ->
            values.associateBy { it.name }
        }
    }

    private fun persistentFlowFor(name: String) = MutablePersistentFlow.serialized(
        "rendered.${konstructionModel.workspaceId}.${konstructionModel.konstructionId}.item.$name",
        DisplayTarget(name, "#ffffff", false)
    )

    init {
        coroutineScope.launch {
            konstructionModel.info.collect { info ->
                val targets = info.targets.associateBy { it.name }
                numberOfTargets.set(targets.keys)
            }
        }
        coroutineScope.launch {
            allTargets.map {
                it.filter { it.value.isEnabled }.map { it.key }
            }.collect { enabledModels ->
                konstructionModel.setTargets(enabledModels)
            }
        }
    }

    fun setTargetEnabled(target: String, enabled: Boolean) {
        val persistentFlow = targetFlows.value[target] ?: persistentFlowFor(target)
        persistentFlow.set(persistentFlow.get().copy(isEnabled = enabled))
    }

    fun setTargetColor(target: String, color: String) {
        val persistentFlow = targetFlows.value[target] ?: persistentFlowFor(target)
        persistentFlow.set(persistentFlow.get().copy(color = color))
    }
}
