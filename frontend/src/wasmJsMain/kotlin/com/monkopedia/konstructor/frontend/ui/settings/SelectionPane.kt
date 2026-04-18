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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.monkopedia.konstructor.common.KonstructionTarget
import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import com.monkopedia.konstructor.frontend.viewmodel.TargetDisplay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SelectionPane(modifier: Modifier = Modifier) {
    val konstructionVm = koinInject<KonstructionViewModel>()
    val info by konstructionVm.info.collectAsState()
    val displays by konstructionVm.targetDisplays.collectAsState()
    val targets = info?.targets ?: emptyList()

    var colorPickerTarget by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "Render Targets",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (targets.isEmpty()) {
            item {
                Text(
                    text = "No targets available. Compile first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(targets, key = { it.name }) { target ->
            val display = displays[target.name] ?: TargetDisplay(name = target.name)
            RenderTargetRow(
                target = target,
                display = display,
                onToggle = { konstructionVm.setTargetEnabled(target.name, it) },
                onPickColor = { colorPickerTarget = target.name }
            )
        }
    }

    val pickerName = colorPickerTarget
    if (pickerName != null) {
        val current = displays[pickerName] ?: TargetDisplay(name = pickerName)
        ColorPickerDialog(
            targetName = pickerName,
            currentColor = current.color,
            onSelect = { color ->
                konstructionVm.setTargetColor(pickerName, color)
                colorPickerTarget = null
            },
            onDismiss = { colorPickerTarget = null }
        )
    }
}

@Composable
private fun RenderTargetRow(
    target: KonstructionTarget,
    display: TargetDisplay,
    onToggle: (Boolean) -> Unit,
    onPickColor: () -> Unit
) {
    val konstructionVm = koinInject<KonstructionViewModel>()
    val scope = rememberCoroutineScope()
    val targetColor = runCatching { Color(parseHex(display.color)) }
        .getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = target.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = {
            scope.launch {
                try {
                    val path = konstructionVm.getKonstructedPath(target.name)
                    if (path != null) {
                        kotlinx.browser.window.open(
                            "${kotlinx.browser.window.location.origin}/$path",
                            "_blank"
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Download ${target.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onPickColor) {
            Icon(
                imageVector = Icons.Filled.ColorLens,
                contentDescription = "Color for ${target.name}",
                tint = targetColor
            )
        }
        Switch(
            checked = display.isEnabled,
            onCheckedChange = onToggle
        )
    }
}

private val PRESET_COLORS = listOf(
    "#000000", "#cc0000", "#4e9a06", "#c4a000",
    "#729fcf", "#75507b", "#06989a", "#d3d7cf",
    "#555753", "#ef2929", "#8ae234", "#fce94f",
    "#32afff", "#ad7fa8", "#34e2e2", "#ffffff"
)

@Composable
private fun ColorPickerDialog(
    targetName: String,
    currentColor: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color for $targetName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (colorRow in PRESET_COLORS.chunked(8)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (hex in colorRow) {
                            val isSelected = hex.equals(currentColor, ignoreCase = true)
                            val color = Color(parseHex(hex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = color,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onSelect(hex) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun parseHex(hex: String): Long {
    val clean = hex.removePrefix("#")
    return (0xFF000000L or clean.toLong(16))
}
