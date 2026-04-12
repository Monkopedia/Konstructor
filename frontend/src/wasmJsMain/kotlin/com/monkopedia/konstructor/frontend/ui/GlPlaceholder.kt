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
package com.monkopedia.konstructor.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.monkopedia.konstructor.frontend.threejs.ThreeJsRenderer
import com.monkopedia.konstructor.frontend.threejs.consoleLog
import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import org.koin.compose.koinInject

/**
 * Initializes the Three.js renderer in the separate #gl-pane HTML div.
 * This composable doesn't render any Compose UI — it just manages the
 * Three.js lifecycle based on ViewModel state.
 */
@Composable
fun InitGlRenderer() {
    val konstructionVm = koinInject<KonstructionViewModel>()
    val settingsVm = koinInject<com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel>()
    val enabledTargets by konstructionVm.enabledRenderedTargets.collectAsState()
    val showFps by settingsVm.showFps.collectAsState()
    val showCameraWidget by settingsVm.showCameraWidget.collectAsState()
    val showCodeLeft by settingsVm.showCodeLeft.collectAsState()
    val ambientIntensity by settingsVm.ambientLightIntensity.collectAsState()
    val directionalLights by settingsVm.directionalLights.collectAsState()

    var renderer by remember { mutableStateOf<ThreeJsRenderer?>(null) }

    DisposableEffect(Unit) {
        consoleLog("InitGlRenderer: creating ThreeJsRenderer")
        val r = ThreeJsRenderer("konstructor-gl-canvas")
        renderer = r
        r.fillContainer("gl-pane")
        onDispose {
            r.dispose()
            renderer = null
        }
    }

    // Reconcile meshes whenever the enabled-and-rendered target set changes
    LaunchedEffect(renderer, enabledTargets) {
        val r = renderer ?: return@LaunchedEffect
        consoleLog("InitGlRenderer: setTargets size=${enabledTargets.size}")
        r.setTargets(enabledTargets)
    }

    // Wire settings to renderer
    LaunchedEffect(renderer, showFps) {
        renderer?.setShowFps(showFps)
    }
    LaunchedEffect(renderer, showCameraWidget) {
        renderer?.setShowAxesHelper(showCameraWidget)
    }
    LaunchedEffect(renderer, ambientIntensity) {
        renderer?.setAmbientIntensity(ambientIntensity)
    }
    LaunchedEffect(renderer, directionalLights) {
        renderer?.setDirectionalLights(
            directionalLights.map {
                ThreeJsRenderer.DirectionalLightInput(
                    intensity = it.intensity.toDouble(),
                    x = it.x.toDouble(),
                    y = it.y.toDouble(),
                    z = it.z.toDouble()
                )
            }
        )
    }
    LaunchedEffect(showCodeLeft) {
        com.monkopedia.konstructor.frontend.threejs.setCodeOnLeft(showCodeLeft)
    }
}
