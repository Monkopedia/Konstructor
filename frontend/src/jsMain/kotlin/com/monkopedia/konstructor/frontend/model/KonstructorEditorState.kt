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
package com.monkopedia.konstructor.frontend.model

import codemirror.themeonedark.oneDark
import com.monkopedia.konstructor.common.MessageImportance.ERROR
import com.monkopedia.konstructor.frontend.editor.MirrorStyles
import com.monkopedia.konstructor.frontend.editor.asString
import com.monkopedia.konstructor.frontend.editor.filterMarks
import com.monkopedia.konstructor.frontend.editor.markField
import com.monkopedia.konstructor.frontend.editor.replaceMarks
import com.monkopedia.konstructor.frontend.utils.buildExt
import dukat.codemirror.basicSetup
import dukat.codemirror.commands.history
import dukat.codemirror.language.StreamLanguage
import dukat.codemirror.legacymodes.kotlin
import dukat.codemirror.state.EditorSelection
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.`T$5`
import dukat.codemirror.state.Text
import dukat.codemirror.state.Transaction
import dukat.codemirror.state.TransactionSpec
import dukat.codemirror.view.Decoration
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.ViewUpdate
import dukat.codemirror.view.scrollPastEnd
import dukat.codemirror.vim.vim
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import styled.getClassSelector

class KonstructorEditorState(
    private val model: KonstructionModel,
    private val coroutineScope: CoroutineScope,
) {
    private val heightTheme = EditorView.theme(
        buildExt {
            set(
                "&",
                kotlinext.js.js {
                    height = "calc(100vh - 64px)"
                    width = "calc(50hw)"
                },
            )
            set(".cm-scroller", kotlinext.js.js { overflow = "auto" })
        },
    )
    var editorState = EditorState.create(
        buildExt {
            this.doc = ""
            this.extensions = arrayOf(
                oneDark,
                vim(),
                basicSetup,
                StreamLanguage.define(kotlin),
                markField,
                EditorView.lineWrapping,
                EditorView.updateListener.of(::onViewUpdate),
                scrollPastEnd(),
                heightTheme,
                history(),
            )
        },
    )

    private val errorClass by lazy {
        MirrorStyles.getClassSelector { it::errorLineBackground }.trimStart('.')
    }
    private val warningClass by lazy {
        MirrorStyles.getClassSelector { it::warningLineBackground }.trimStart('.')
    }

    private val currentLine = MutableStateFlow(-1)
    private val currentPos = MutableStateFlow(-1)
    private var currentView = MutableStateFlow<EditorView?>(null)
    val setViewAvailable: (EditorView, Boolean) -> Unit = { view, available ->
        currentView.update {
            view.takeIf { available } ?: it.takeIf { it != view }
        }
    }

    private val backendText = model.currentText.filterNotNull()
    private val desiredText = MutableStateFlow("")
    private val currentText = MutableStateFlow("")
    val hasLocalChanges = combine(desiredText, currentText) { desired, current ->
        val desiredRaw = desired
            .replace(" ", "")
            .replace("\n", "")
            .replace("\t", "")
        val currentRaw = current
            .replace(" ", "")
            .replace("\n", "")
            .replace("\t", "")
        desiredRaw != currentRaw
    }
    val conflictingText = MutableStateFlow<Boolean>(false)
    private val classes = model.messages.map { messages ->
        mapOf(
            errorClass to messages.filter { it.importance == ERROR }
                .mapNotNull { it.line },
            warningClass to messages.filter { it.importance != ERROR }
                .mapNotNull { it.line },
        )
    }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    private data class EditorTextState(
        val backendText: TextSegment,
        val desiredText: TextSegment,
        val currentText: TextSegment,
        val classes: Map<String, List<Int>>,
    )

    init {
        val initializeJob = coroutineScope.async {
            val initialText = backendText.filterNotNull().first()
            currentText.value = initialText
            desiredText.value = initialText
        }
        currentView.launchCollectLatest(coroutineScope) { view ->
            combine(backendText, desiredText, currentText, classes) { backend, desired, current, classes ->
                EditorTextState(
                    TextSegment(backend),
                    TextSegment(desired),
                    TextSegment(current),
                    classes,
                )
            }.transform { (backend, desired, current, classes) ->
                if (backend.raw != desired.raw) {
                    if (backend.raw == current.raw) {
                        desiredText.value = current.text
                    } else if (desired.raw == current.raw) {
                        desiredText.value = desired.text
                        emit(setTransaction(current.text.length, desired.text))
                    } else {
                        conflictingText.value = true
                    }
                } else if (desired.raw == current.raw) {
                    emit(setMarks(editorState.doc, classes))
                } else {
                    emit(setMarks(editorState.doc, emptyMap()))
                }
            }.onStart {
                initializeJob.await()
                val lastPos = currentPos.value
                emit(setTransaction(editorState.doc.length, currentText.value))
                lastPos.takeIf { it >= 0 }?.let {
                    emit(setPosition(it))
                }
            }.collect { transaction ->
                view?.dispatch(transaction)
                    ?: editorState.update(transaction).also {
                        editorState = it.state
                    }
            }
        }
    }

    suspend fun discardLocalChanges() {
        desiredText.value = currentText.value
        conflictingText.value = false
    }

    val currentMessage: Flow<String?> =
        combine(currentLine, model.messages, hasLocalChanges) { line, messages, hasLocalChanges ->
            messages.find {
                it.line == line
            }?.message.takeIf { !hasLocalChanges }
        }

    private fun onViewUpdate(viewUpdate: ViewUpdate) {
        val pos = viewUpdate.state.selection.main.head.toInt()
        val line = viewUpdate.state.doc.lineAt(pos).number.toInt() - 1
        currentText.value = viewUpdate.state.doc.asString()
        currentLine.value = line
        currentPos.value = pos
    }

    fun save(): String {
        return currentText.value.also {
            conflictingText.value = false
        }
    }
}

fun <T> Flow<T>.launchCollectLatest(
    scope: CoroutineScope,
    consumer: suspend CoroutineScope.(T) -> Unit,
) {
    scope.launch {
        collectLatest { value ->
            coroutineScope {
                consumer(value)
            }
        }
    }
}

private value class TextSegment(val text: String) {
    val raw: String
        get() = text.orEmpty()
            .replace(" ", "")
            .replace("\n", "")
            .replace("\t", "")
}

private fun setMarks(doc: Text, customClasses: Map<String, List<Int>>): TransactionSpec {
    val marks = customClasses.entries.flatMap { (key, lines) ->
        lines.filter {
            (it + 1) <= doc.lines.toInt()
        }.map {
            doc.line(it + 1).let { lineInfo ->
                Decoration.mark(
                    buildExt {
                        this.`class` = key
                    },
                ).range(lineInfo.from, max(lineInfo.to.toInt(), lineInfo.from.toInt() + 1))
            }
        }
    }
    return buildExt {
        annotations = listOf(Transaction.addToHistory.of(false))
        effects = replaceMarks.of(marks.toTypedArray())
    }
}

private fun setTransaction(oldLength: Number, content: String) = buildExt<TransactionSpec> {
    annotations = listOf(Transaction.addToHistory.of(false))
    effects = filterMarks.of { _, _, _ -> false }
    changes = buildExt<`T$5`> {
        from = 0
        to = oldLength
        insert = content
    }
}

private fun setPosition(position: Int) = buildExt<TransactionSpec> {
    selection = EditorSelection.cursor(position)
    scrollIntoView = true
}
