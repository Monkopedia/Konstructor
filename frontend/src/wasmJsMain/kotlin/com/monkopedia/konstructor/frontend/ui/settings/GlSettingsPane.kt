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
package com.monkopedia.konstructor.frontend.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.monkopedia.konstructor.frontend.viewmodel.DirectionalLightConfig
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import org.koin.compose.koinInject

private data class DirectionalLightState(
    val intensity: Float = 0.5f,
    val x: String = "0",
    val y: String = "0",
    val z: String = "-1"
) {
    fun toConfig(): DirectionalLightConfig = DirectionalLightConfig(
        intensity = intensity,
        x = x.toFloatOrNull() ?: 0f,
        y = y.toFloatOrNull() ?: 0f,
        z = z.toFloatOrNull() ?: 0f
    )
}

private fun DirectionalLightConfig.toState(): DirectionalLightState =
    DirectionalLightState(intensity, x.toString(), y.toString(), z.toString())

@Composable
fun GlSettingsPane(modifier: Modifier = Modifier) {
    val settingsVm = koinInject<SettingsViewModel>()
    val ambientIntensity by settingsVm.ambientLightIntensity.collectAsState()
    val savedLights by settingsVm.directionalLights.collectAsState()

    // Keep a local mutable state list that mirrors the persisted list for fluid editing
    val lights = remember { mutableStateListOf<DirectionalLightState>() }
    androidx.compose.runtime.LaunchedEffect(savedLights) {
        // Only reset local state if it differs from saved (to avoid clobbering in-progress edits)
        val savedStates = savedLights.map { it.toState() }
        if (lights.toList() != savedStates) {
            lights.clear()
            lights.addAll(savedStates)
        }
    }

    fun persist() {
        settingsVm.setDirectionalLights(lights.map { it.toConfig() })
    }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Ambient Light",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = ambientIntensity,
                onValueChange = { settingsVm.setAmbientLightIntensity(it) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Directional Lights",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = {
                    lights.add(DirectionalLightState())
                    persist()
                }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add light",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        itemsIndexed(lights) { index, light ->
            DirectionalLightCard(
                index = index,
                light = light,
                onDelete = {
                    lights.removeAt(index)
                    persist()
                },
                onUpdate = {
                    lights[index] = it
                    persist()
                }
            )
        }
    }
}

@Composable
private fun DirectionalLightCard(
    index: Int,
    light: DirectionalLightState,
    onDelete: () -> Unit,
    onUpdate: (DirectionalLightState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Light ${index + 1}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete light",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            text = "Intensity",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = light.intensity,
            onValueChange = { onUpdate(light.copy(intensity = it)) },
            valueRange = 0f..2f,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = light.x,
                onValueChange = { onUpdate(light.copy(x = it)) },
                label = { Text("X") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = light.y,
                onValueChange = { onUpdate(light.copy(y = it)) },
                label = { Text("Y") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = light.z,
                onValueChange = { onUpdate(light.copy(z = it)) },
                label = { Text("Z") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}
