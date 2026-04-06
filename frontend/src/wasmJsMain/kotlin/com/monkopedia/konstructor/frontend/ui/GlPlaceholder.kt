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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import com.monkopedia.konstructor.frontend.threejs.ThreeJsRenderer
import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import org.koin.compose.koinInject

@Composable
fun GlPlaceholder(modifier: Modifier = Modifier) {
    val konstructionVm = koinInject<KonstructionViewModel>()
    val renderPath by konstructionVm.renderPath.collectAsState()
    val density = LocalDensity.current

    var renderer by remember { mutableStateOf<ThreeJsRenderer?>(null) }
    var posX by remember { mutableStateOf(0) }
    var posY by remember { mutableStateOf(0) }
    var width by remember { mutableStateOf(0) }
    var height by remember { mutableStateOf(0) }

    // Create the renderer once
    DisposableEffect(Unit) {
        val r = ThreeJsRenderer("konstructor-threejs-canvas")
        renderer = r
        onDispose {
            r.dispose()
            renderer = null
        }
    }

    // Update renderer size/position when layout changes
    LaunchedEffect(renderer, posX, posY, width, height) {
        renderer?.updateLayout(posX, posY, width, height)
    }

    // Load STL when renderPath changes
    LaunchedEffect(renderer, renderPath) {
        val path = renderPath
        val r = renderer
        if (r != null && path != null) {
            r.loadStl(path)
        } else if (r != null && path == null) {
            r.clearModel()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF263238))
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                with(density) {
                    posX = position.x.toInt()
                    posY = position.y.toInt()
                    width = coordinates.size.width
                    height = coordinates.size.height
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (renderPath == null) {
            Text(
                text = "3D Viewer - Build a target to render",
                color = Color(0xFF80CBC4)
            )
        }
    }
}
