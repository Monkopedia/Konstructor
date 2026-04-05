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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.monkopedia.konstructor.frontend.ui.editor.EditorPane
import com.monkopedia.konstructor.frontend.ui.navigation.NavigationPane
import com.monkopedia.konstructor.frontend.ui.settings.GlSettingsPane
import com.monkopedia.konstructor.frontend.ui.settings.SelectionPane
import com.monkopedia.konstructor.frontend.ui.settings.SettingsPane
import com.monkopedia.konstructor.frontend.viewmodel.CodePaneMode
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import org.koin.compose.koinInject

@Composable
fun ContentPane(modifier: Modifier = Modifier) {
    val settingsVm = koinInject<SettingsViewModel>()
    val codePaneMode by settingsVm.codePaneMode.collectAsState()

    Column(modifier = modifier) {
        TopBar()
        when (codePaneMode) {
            CodePaneMode.EDITOR -> EditorPane(modifier = Modifier.fillMaxSize())
            CodePaneMode.NAVIGATION -> NavigationPane(modifier = Modifier.fillMaxSize())
            CodePaneMode.SETTINGS -> SettingsPane(modifier = Modifier.fillMaxSize())
            CodePaneMode.GL_SETTINGS -> GlSettingsPane(modifier = Modifier.fillMaxSize())
            CodePaneMode.SELECTION -> SelectionPane(modifier = Modifier.fillMaxSize())
        }
    }
}
