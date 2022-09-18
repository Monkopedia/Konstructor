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
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

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
