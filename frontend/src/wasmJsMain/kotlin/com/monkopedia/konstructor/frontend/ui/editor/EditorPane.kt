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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.legacy.modes.kotlin
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.themonedark.oneDark
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.rememberEditorSession
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import com.monkopedia.konstructor.frontend.viewmodel.UiState
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel
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
        // Use key() to force recreation when switching konstructions
        if (selectedKonId != null) {
            EditorContent(
                content = content,
                konstructionVm = konstructionVm,
                selectedKonId = selectedKonId!!
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Select a konstruction to edit", color = Color.Gray)
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
    val kotlinLang = remember { StreamLanguage.define(kotlin).extension }
    val vimExt = remember { com.monkopedia.kodemirror.vim.vim() }
    val extensions = remember { basicSetup + oneDark + kotlinLang + vimExt }

    // Key the session on the konstruction ID AND the content hash
    // so it recreates when content loads or when switching konstructions
    val session = remember(selectedKonId, content) {
        val config = com.monkopedia.kodemirror.state.EditorStateConfig(
            doc = content.asDoc(),
            extensions = extensions
        )
        com.monkopedia.kodemirror.view.EditorSession(
            com.monkopedia.kodemirror.state.EditorState.create(config)
        )
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
