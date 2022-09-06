package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.frontend.utils.MutablePersistentFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.html.Dir
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

class GlControlsModel(private val scope: CoroutineScope) {

    private val mutableAmbientLight = MutablePersistentFlow.int("global.ambient", 0)
    val ambientLight: Flow<Double> = mutableAmbientLight.map { it / 1000.0 }

    @Serializable
    data class DirectionalLight(
        val intensity: Double,
        val x: Double,
        val y: Double,
        val z: Double
    )

    private val mutableLights = MutablePersistentFlow.serialized(
        "global.lights",
        listOf(DirectionalLight(0.5, 0.0, 0.0, -1.0))
    )
    val lights: Flow<List<DirectionalLight>> = mutableLights

    fun setAmbientLight(amount: Double) {
        mutableAmbientLight.set((amount * 1000).roundToInt())
    }

    fun setLights(lights: List<DirectionalLight>) {
        mutableLights.set(lights)
    }
}
