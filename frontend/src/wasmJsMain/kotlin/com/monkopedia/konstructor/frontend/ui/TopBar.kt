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

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.monkopedia.konstructor.frontend.viewmodel.CodePaneMode
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel
import org.koin.compose.koinInject
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    val settingsVm = koinInject<SettingsViewModel>()
    val workspaceVm = koinInject<WorkspaceViewModel>()
    val codePaneMode by settingsVm.codePaneMode.collectAsState()
    val workspaceName by workspaceVm.workspaceName.collectAsState()
    val selectedKonstructionId by workspaceVm.selectedKonstructionId.collectAsState()
    val konstructions by workspaceVm.konstructions.collectAsState()

    val konstructionName = konstructions
        .firstOrNull { it.id == selectedKonstructionId }
        ?.name ?: ""

    val titleText = buildString {
        if (workspaceName.isNotEmpty()) {
            append(workspaceName)
            if (konstructionName.isNotEmpty()) {
                append(" > ")
                append(konstructionName)
            }
        } else {
            append("Konstructor")
        }
    }

    TopAppBar(
        title = {
            Text(
                text = titleText,
                modifier = Modifier.clickable {
                    if (codePaneMode == CodePaneMode.NAVIGATION) {
                        settingsVm.setCodePaneMode(CodePaneMode.EDITOR)
                    } else {
                        settingsVm.setCodePaneMode(CodePaneMode.NAVIGATION)
                    }
                },
                maxLines = 1
            )
        },
        navigationIcon = {
            if (codePaneMode != CodePaneMode.EDITOR) {
                IconButton(onClick = {
                    settingsVm.setCodePaneMode(CodePaneMode.EDITOR)
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to editor"
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = {
                settingsVm.setCodePaneMode(CodePaneMode.SELECTION)
            }) {
                Icon(
                    imageVector = Icons.Filled.Checklist,
                    contentDescription = "Selection"
                )
            }
            IconButton(onClick = {
                settingsVm.setCodePaneMode(CodePaneMode.GL_SETTINGS)
            }) {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = "Lighting"
                )
            }
            IconButton(onClick = {
                settingsVm.setCodePaneMode(CodePaneMode.SETTINGS)
            }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
