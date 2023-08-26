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
package com.monkopedia.konstructor.frontend.logging

import codemirror.themeonedark.oneDark
import com.monkopedia.konstructor.frontend.editor.filterMarks
import com.monkopedia.konstructor.frontend.editor.markField
import com.monkopedia.konstructor.frontend.utils.buildExt
import dukat.codemirror.basicSetup
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.`T$5`
import dukat.codemirror.state.TransactionSpec
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.scrollPastEnd
import dukat.codemirror.vim.vim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LogViewerState(
//    private val model: KonstructionModel,
    private val coroutineScope: CoroutineScope
) {
    private val heightTheme = EditorView.theme(
        buildExt {
//            set(
//                "&",
//                kotlinext.js.js {
//                    height = "calc(100vh - 64px)"
//                    width = "calc(50hw)"
//                }
//            )
            set(".cm-scroller", kotlinext.js.js { overflow = "auto" })
        }
    )
    var editorState = EditorState.create(
        buildExt {
            this.doc = ""
            this.extensions = arrayOf(
                oneDark,
                vim(),
                basicSetup,
                markField,
                EditorView.lineWrapping,
                scrollPastEnd(),
                heightTheme
            )
        }
    )

    private val transactionFlow = MutableSharedFlow<TransactionSpec>()

    private var currentView: EditorView? = null

    val setView: (EditorView?) -> Unit = {
        currentView = it
    }

    private fun setTransaction(content: String) = buildExt<TransactionSpec> {
        effects = filterMarks.of { _, _, _ -> false }
        changes = buildExt<`T$5`> {
            from = 0
            to = editorState.doc.length
            insert = content
        }
    }

    private val currentText = MutableStateFlow("")
    private val currentSetText = MutableStateFlow("")
    val pendingText = MutableStateFlow<String?>(null)
    private val setText = MutableStateFlow<String?>(null)

    init {
        coroutineScope.launch {
            transactionFlow.collect { transaction ->
                currentView?.dispatch(transaction)
                    ?: editorState.update(transaction).also {
                        editorState = it.state
                    }
            }
        }
        coroutineScope.launch {
            val initialText = setText.filterNotNull().first()
            currentText.value = initialText
            currentSetText.value = initialText
            transactionFlow.emit(setTransaction(initialText))
            launch {
                setText.filterNotNull().collect { newText ->
                    if (newText == currentText.value) return@collect
                    if (currentText.value == currentSetText.value) {
                        updateText(newText)
                        return@collect
                    }
                    val rawText = currentText.value
                        .replace(" ", "")
                        .replace("\n", "")
                        .replace("\t", "")
                    val newRawText = newText
                        .replace(" ", "")
                        .replace("\n", "")
                        .replace("\t", "")
                    if (rawText != newRawText) {
                        pendingText.value = newText
                    } else {
                        updateText(newText)
                    }
                }
            }
        }
    }

    fun setText(text: String) {
        setText.value = text
    }

    private suspend fun updateText(newText: String) {
        transactionFlow.emit(setTransaction(newText))
        currentText.value = newText
        currentSetText.value = newText
    }
}
