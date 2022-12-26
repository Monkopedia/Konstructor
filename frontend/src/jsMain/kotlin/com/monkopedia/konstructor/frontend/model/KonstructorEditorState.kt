package com.monkopedia.konstructor.frontend.model

import codemirror.themeonedark.oneDark
import com.monkopedia.konstructor.common.MessageImportance.ERROR
import com.monkopedia.konstructor.frontend.editor.MirrorStyles
import com.monkopedia.konstructor.frontend.editor.addMarks
import com.monkopedia.konstructor.frontend.editor.asString
import com.monkopedia.konstructor.frontend.editor.filterMarks
import com.monkopedia.konstructor.frontend.editor.markField
import com.monkopedia.konstructor.frontend.editor.replaceMarks
import com.monkopedia.konstructor.frontend.utils.buildExt
import dukat.codemirror.basicSetup
import dukat.codemirror.commands.history
import dukat.codemirror.language.StreamLanguage
import dukat.codemirror.legacymodes.kotlin
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.`T$5`
import dukat.codemirror.state.TransactionSpec
import dukat.codemirror.view.Decoration
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.ViewUpdate
import dukat.codemirror.view.scrollPastEnd
import dukat.codemirror.vim.vim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import styled.getClassSelector
import kotlin.math.max

class KonstructorEditorState(
    private val model: KonstructionModel,
    private val coroutineScope: CoroutineScope
) {
    private val heightTheme = EditorView.theme(
        buildExt {
            set(
                "&",
                kotlinext.js.js {
                    height = "calc(100vh - 64px)"
                    width = "calc(50hw)"
                }
            )
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
                StreamLanguage.define(kotlin),
                markField,
                EditorView.lineWrapping,
                EditorView.updateListener.of(::onViewUpdate),
                scrollPastEnd(),
                heightTheme,
                history()
            )
        }
    )

    private val errorClass by lazy {
        MirrorStyles.getClassSelector { it::errorLineBackground }.trimStart('.')
    }
    private val warningClass by lazy {
        MirrorStyles.getClassSelector { it::warningLineBackground }.trimStart('.')
    }
    private val classes = model.messages.map { messages ->
        mapOf(
            errorClass to messages.filter { it.importance == ERROR }
                .mapNotNull { it.line },
            warningClass to messages.filter { it.importance != ERROR }
                .mapNotNull { it.line }
        )
    }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())
    private val transactionFlow = MutableSharedFlow<TransactionSpec>()

    private val currentLine = MutableStateFlow(-1)
    private var currentView: EditorView? = null
    val setView: (EditorView?) -> Unit = {
        currentView = it
    }

    private fun setMarks(customClasses: Map<String, List<Int>>): TransactionSpec {
        val doc = editorState.doc

        val marks = customClasses.entries.flatMap { (key, lines) ->
            lines.filter {
                (it + 1) <= doc.lines.toInt()
            }.map {
                doc.line(it + 1).let { lineInfo ->
                    Decoration.mark(
                        buildExt {
                            this.`class` = key
                        }
                    ).range(lineInfo.from, max(lineInfo.to.toInt(), lineInfo.from.toInt() + 1))
                }
            }
        }
        return buildExt {
            effects = replaceMarks.of(marks.toTypedArray())
        }
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
            val initialText = model.currentText.filterNotNull().first()
            currentText.value = initialText
            currentSetText.value = initialText
            transactionFlow.emit(setTransaction(initialText))
            launch {
                model.currentText.filterNotNull().collect { newText ->
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
            classes.collect { classes ->
                transactionFlow.emit(setMarks(classes))
            }
        }
    }

    suspend fun discardLocalChanges() {
        updateText(pendingText.value ?: return)
        pendingText.value = null
    }

    private suspend fun updateText(newText: String) {
        transactionFlow.emit(setTransaction(newText))
        transactionFlow.emit(setMarks(classes.value))
        currentText.value = newText
        currentSetText.value = newText
    }

    val currentMessage: Flow<String?> = combine(currentLine, model.messages) { line, messages ->
        messages.find {
            it.line == line
        }?.message
    }

    private fun onViewUpdate(viewUpdate: ViewUpdate) {
        val pos = viewUpdate.state.selection.main.head.toInt()
        val line = viewUpdate.state.doc.lineAt(pos).number.toInt() - 1
        currentText.value = viewUpdate.state.doc.asString()
        currentLine.value = line
    }

    fun save(): String {
        return currentText.value.also {
            pendingText.value = null
        }
    }
}
