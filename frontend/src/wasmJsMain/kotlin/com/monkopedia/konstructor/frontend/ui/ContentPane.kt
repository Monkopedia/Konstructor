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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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

// Ordering used to determine horizontal slide direction between side panels
private val CodePaneMode.order: Int
    get() = when (this) {
        CodePaneMode.SELECTION -> 0
        CodePaneMode.GL_SETTINGS -> 1
        CodePaneMode.SETTINGS -> 2
        CodePaneMode.NAVIGATION -> 3
        CodePaneMode.EDITOR -> 4
    }

private fun transitionSpec(
    from: CodePaneMode,
    to: CodePaneMode
): Pair<EnterTransition, ExitTransition> {
    // Editor lives "below" navigation — vertical slide + fade
    if (from == CodePaneMode.NAVIGATION && to == CodePaneMode.EDITOR) {
        return fadeIn() + slideInVertically { it / 4 } to
            fadeOut() + slideOutVertically { -it / 4 }
    }
    if (from == CodePaneMode.EDITOR && to == CodePaneMode.NAVIGATION) {
        return fadeIn() + slideInVertically { -it / 4 } to
            fadeOut() + slideOutVertically { it / 4 }
    }

    // Editor ↔ side panels: horizontal slide
    if (from == CodePaneMode.EDITOR) {
        return fadeIn() + slideInHorizontally { it / 4 } to
            fadeOut() + slideOutHorizontally { -it / 4 }
    }
    if (to == CodePaneMode.EDITOR) {
        return fadeIn() + slideInHorizontally { -it / 4 } to
            fadeOut() + slideOutHorizontally { it / 4 }
    }

    // Between side panels / navigation: horizontal slide based on order
    val direction = if (to.order > from.order) 1 else -1
    return fadeIn() + slideInHorizontally { direction * it / 4 } to
        fadeOut() + slideOutHorizontally { -direction * it / 4 }
}

@Composable
fun ContentPane(modifier: Modifier = Modifier) {
    val settingsVm = koinInject<SettingsViewModel>()
    val codePaneMode by settingsVm.codePaneMode.collectAsState()

    Column(modifier = modifier) {
        TopBar()
        AnimatedContent(
            targetState = codePaneMode,
            transitionSpec = {
                val (enter, exit) = transitionSpec(initialState, targetState)
                enter togetherWith exit
            },
            modifier = Modifier.fillMaxSize()
        ) { mode ->
            when (mode) {
                CodePaneMode.EDITOR -> EditorPane(modifier = Modifier.fillMaxSize())
                CodePaneMode.NAVIGATION -> NavigationPane(modifier = Modifier.fillMaxSize())
                CodePaneMode.SETTINGS -> SettingsPane(modifier = Modifier.fillMaxSize())
                CodePaneMode.GL_SETTINGS -> GlSettingsPane(modifier = Modifier.fillMaxSize())
                CodePaneMode.SELECTION -> SelectionPane(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
