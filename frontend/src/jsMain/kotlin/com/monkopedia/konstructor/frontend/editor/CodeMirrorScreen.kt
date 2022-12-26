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
package com.monkopedia.konstructor.frontend.editor

import codemirror.themeonedark.oneDark
import com.monkopedia.konstructor.frontend.utils.buildExt
import dukat.codemirror.basicSetup
import dukat.codemirror.commands.history
import dukat.codemirror.language.StreamLanguage
import dukat.codemirror.legacymodes.kotlin
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.`T$5`
import dukat.codemirror.state.Text
import dukat.codemirror.state.Transaction
import dukat.codemirror.state.TransactionSpec
import dukat.codemirror.view.Decoration
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.ViewUpdate
import dukat.codemirror.view.scrollPastEnd
import dukat.codemirror.vim.CodeMirror
import dukat.codemirror.vim.vim
import emotion.react.css
import kotlinext.js.js
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.css.background
import org.w3c.dom.HTMLDivElement
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.memo
import react.useEffect
import react.useRef
import react.useState
import styled.StyleSheet
import styled.getClassSelector
import kotlin.math.max

internal object MirrorStyles : StyleSheet("mirror", isStatic = true) {
    val errorLineBackground by css {
        background = "#FF000033"
    }
    val warningLineBackground by css {
        background = "#FFB81C33"
    }
}

external interface CodeMirrorProps : Props {
    var editorState: EditorState
    var setView: (EditorView?) -> Unit
    var target: String
    var onSave: ((String) -> Unit)?
}

val CodeMirrorScreen = memo(
    FC<CodeMirrorProps> { props ->
        MirrorStyles.errorLineBackground
        MirrorStyles.inject()
        val textAreaRef = useRef<HTMLDivElement>()
        val saveRef = useRef<(String) -> Unit>()

        div {
            this.ref = textAreaRef
        }
        saveRef.current = props.onSave

        useEffect(textAreaRef, props.editorState) {
            val textArea = textAreaRef.current!!
            val cm = EditorView(
                buildExt {
                    this.state = props.editorState
                    this.parent = textArea
                }
            )

            fun onSave(cm: CodeMirror) {
                saveRef.current?.invoke(cm.getValue())
            }
            CodeMirror(cm)
            CodeMirror.commands.asDynamic().save = ::onSave
            props.setView(cm)
            cleanup {
                props.setView(null)
                cm.destroy()
            }
        }
    }
) { oldProps, newProps ->
    oldProps.editorState === newProps.editorState &&
        oldProps.target == newProps.target
}

fun Text.asString(): String {
    return (0 until lines.toInt()).joinToString("\n") { line(it + 1).text }
}
