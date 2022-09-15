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

    private val numberOfTargets =
        MutablePersistentFlow.serialized("rendered.${konstructionModel.workspaceId}.${konstructionModel.konstructionId}.keys", emptySet<String>())
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

    private fun persistentFlowFor(name: String) =
        MutablePersistentFlow.serialized(
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
            allTargets.map { it.filter { it.value.isEnabled }.map { it.key } }.collect { enabledModels ->
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
