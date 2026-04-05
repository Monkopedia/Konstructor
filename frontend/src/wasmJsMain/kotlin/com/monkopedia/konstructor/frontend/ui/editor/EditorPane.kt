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
import com.monkopedia.kodemirror.view.setDoc
import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import com.monkopedia.konstructor.frontend.viewmodel.UiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditorPane(modifier: Modifier = Modifier) {
    val konstructionVm = koinViewModel<KonstructionViewModel>()
    val content by konstructionVm.content.collectAsState()
    val uiState by konstructionVm.state.collectAsState()
    val messages by konstructionVm.messages.collectAsState()

    val kotlinLang = remember { StreamLanguage.define(kotlin).extension }
    val extensions = remember { basicSetup + oneDark + kotlinLang }
    val session = rememberEditorSession(
        doc = content,
        extensions = extensions
    )

    // Update editor when content changes from server
    LaunchedEffect(content) {
        val currentDoc = session.state.doc.toString()
        if (currentDoc != content) {
            session.setDoc(content)
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

        // Editor
        Box(modifier = Modifier.fillMaxSize()) {
            KodeMirror(
                session = session,
                modifier = Modifier.fillMaxSize()
            )
        }
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
