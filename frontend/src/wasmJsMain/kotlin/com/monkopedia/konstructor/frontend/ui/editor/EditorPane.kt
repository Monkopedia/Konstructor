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
package com.monkopedia.konstructor.frontend.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.commands.emacsStyleKeymap
import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.legacy.modes.kotlin
import com.monkopedia.kodemirror.materialtheme.rememberMaterialEditorTheme
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.themeamy.amy
import com.monkopedia.kodemirror.themeayulight.ayuLight
import com.monkopedia.kodemirror.themebarf.barf
import com.monkopedia.kodemirror.themebespin.bespin
import com.monkopedia.kodemirror.themebirdsofparadise.birdsOfParadise
import com.monkopedia.kodemirror.themeboysandgirls.boysAndGirls
import com.monkopedia.kodemirror.themeclouds.clouds
import com.monkopedia.kodemirror.themecobalt.cobalt
import com.monkopedia.kodemirror.themecoolglow.coolGlow
import com.monkopedia.kodemirror.themedracula.dracula
import com.monkopedia.kodemirror.themeespresso.espresso
import com.monkopedia.kodemirror.themenoctislilac.noctisLilac
import com.monkopedia.kodemirror.themonedark.oneDark
import com.monkopedia.kodemirror.themerosepinedawn.rosePineDawn
import com.monkopedia.kodemirror.themesmoothy.smoothy
import com.monkopedia.kodemirror.themesolarizedlight.solarizedLight
import com.monkopedia.kodemirror.themetomorrow.tomorrow
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.editorContentStyle
import com.monkopedia.kodemirror.view.keymapOf
import com.monkopedia.kodemirror.view.onSave
import com.monkopedia.kodemirror.view.saveKeymap
import com.monkopedia.konstructor.frontend.viewmodel.EditorThemeName
import com.monkopedia.konstructor.frontend.viewmodel.KeymapName
import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.UiState
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel
import konstructor.frontend.generated.resources.JetBrainsMono_Regular
import konstructor.frontend.generated.resources.Res
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.koin.compose.koinInject

@Composable
fun EditorPane(modifier: Modifier = Modifier) {
    val konstructionVm = koinInject<KonstructionViewModel>()
    val workspaceVm = koinInject<WorkspaceViewModel>()
    val selectedKonId by workspaceVm.selectedKonstructionId.collectAsState()
    val konstructions by workspaceVm.konstructions.collectAsState()
    val content by konstructionVm.content.collectAsState()
    val uiState by konstructionVm.state.collectAsState()
    val messages by konstructionVm.messages.collectAsState()

    // Load konstruction content when selection changes
    LaunchedEffect(selectedKonId, konstructions) {
        val k = konstructions.firstOrNull { it.id == selectedKonId }
        if (k != null) {
            konstructionVm.loadKonstruction(k)
        }
    }

    Column(modifier = modifier) {
        // Status bar
        when (uiState) {
            UiState.LOADING -> StatusBar("Loading...", Color(0xFF64B5F6))
            UiState.SAVING -> StatusBar("Saving...", Color(0xFFFFB74D))
            UiState.COMPILING -> StatusBar("Compiling...", Color(0xFFFFB74D))
            UiState.EXECUTING -> StatusBar("Executing...", Color(0xFFFFB74D))
            UiState.DEFAULT -> {
                if (messages.isNotEmpty()) {
                    StatusBar(
                        "${messages.size} message(s)",
                        Color(0xFFEF5350)
                    )
                }
            }
        }

        // Only render editor when we have a selected konstruction
        val selectedKonstruction = konstructions.firstOrNull { it.id == selectedKonId }
        when {
            selectedKonId == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select a konstruction to edit", color = Color.Gray)
                }
            }
            selectedKonstruction?.type == com.monkopedia.konstructor.common.KonstructionType.STL -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "STL file — view in 3D pane",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                EditorContent(
                    content = content,
                    konstructionVm = konstructionVm,
                    selectedKonId = selectedKonId!!
                )
            }
        }
    }
}

@Composable
private fun EditorContent(
    content: String,
    konstructionVm: KonstructionViewModel,
    selectedKonId: String
) {
    val scope = rememberCoroutineScope()
    val settingsVm = koinInject<SettingsViewModel>()
    val themeName by settingsVm.editorTheme.collectAsState()
    val keymapName by settingsVm.keymap.collectAsState()

    val kotlinLang = remember { StreamLanguage.define(kotlin).extension }

    // Font must be loaded in composable context
    val monoFont = FontFamily(Font(Res.font.JetBrainsMono_Regular))
    val fontExt = editorContentStyle.of(TextStyle(fontFamily = monoFont))

    // Theme extension — Material theme needs composable context
    val materialTheme = rememberMaterialEditorTheme()
    val themeExt: Extension = when (themeName) {
        EditorThemeName.DRACULA -> dracula
        EditorThemeName.ONE_DARK -> oneDark
        EditorThemeName.AMY -> amy
        EditorThemeName.AYU_LIGHT -> ayuLight
        EditorThemeName.BARF -> barf
        EditorThemeName.BESPIN -> bespin
        EditorThemeName.BIRDS_OF_PARADISE -> birdsOfParadise
        EditorThemeName.BOYS_AND_GIRLS -> boysAndGirls
        EditorThemeName.CLOUDS -> clouds
        EditorThemeName.COBALT -> cobalt
        EditorThemeName.COOL_GLOW -> coolGlow
        EditorThemeName.ESPRESSO -> espresso
        EditorThemeName.NOCTIS_LILAC -> noctisLilac
        EditorThemeName.ROSE_PINE_DAWN -> rosePineDawn
        EditorThemeName.SMOOTHY -> smoothy
        EditorThemeName.SOLARIZED_LIGHT -> solarizedLight
        EditorThemeName.TOMORROW -> tomorrow
        EditorThemeName.MATERIAL -> materialTheme
    }

    // Keymap extension
    val keymapExt: Extension = when (keymapName) {
        KeymapName.VIM -> com.monkopedia.kodemirror.vim.vim()
        KeymapName.EMACS -> keymapOf(*emacsStyleKeymap.toTypedArray())
        KeymapName.DEFAULT -> extensionListOf() // basicSetup already includes default keymap
    }

    // Save handler via onSave facet — works with both Ctrl+S keymap and vim :w
    val saveExt = onSave.of { session ->
        konstructionVm.save(session.state.doc.toString())
    }

    // Recreate session when konstruction, theme, or keymap changes
    val session = remember(selectedKonId, themeName, keymapName, monoFont) {
        val extensions = basicSetup + themeExt + fontExt + kotlinLang + keymapExt + saveKeymap + saveExt
        val config = com.monkopedia.kodemirror.state.EditorStateConfig(
            doc = content.asDoc(),
            extensions = extensions
        )
        com.monkopedia.kodemirror.view.EditorSession(
            com.monkopedia.kodemirror.state.EditorState.create(config)
        )
    }

    // When content changes externally (server load, not user save), update
    // the editor document without recreating the session — preserves undo history.
    LaunchedEffect(content) {
        val currentDoc = session.state.doc.toString()
        if (currentDoc != content && content.isNotEmpty()) {
            session.dispatch(
                com.monkopedia.kodemirror.state.TransactionSpec(
                    changes = com.monkopedia.kodemirror.state.ChangeSpec.Single(
                        from = com.monkopedia.kodemirror.state.DocPos.ZERO,
                        to = com.monkopedia.kodemirror.state.DocPos(session.state.doc.length),
                        insert = content.asInsert()
                    )
                )
            )
        }
    }

    // Editor
    Box(modifier = Modifier.fillMaxSize()) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun StatusBar(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
