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
import dukat.codemirror.state.TransactionSpec
import dukat.codemirror.view.Decoration
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.ViewUpdate
import dukat.codemirror.view.scrollPastEnd
import dukat.codemirror.vim.CodeMirror
import dukat.codemirror.vim.vim
import emotion.react.css
import kotlinext.js.js
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

private object MirrorStyles : StyleSheet("mirror", isStatic = true) {
    val errorLineBackground by css {
        background = "#FF000033"
    }
    val warningLineBackground by css {
        background = "#FFB81C33"
    }
}

external interface CodeMirrorProps : Props {
    var content: String?
    var onCursorChange: ((Int) -> Unit)?
    var onSave: ((String) -> Unit)?
    var customClasses: Map<String, List<Int>>?
}

val errorClass by lazy { MirrorStyles.getClassSelector { it::errorLineBackground }.trimStart('.') }
val warningClass by lazy {
    MirrorStyles.getClassSelector { it::warningLineBackground }.trimStart('.')
}

val CodeMirrorScreen = memo(
    FC<CodeMirrorProps> { props ->
        MirrorStyles.errorLineBackground
        MirrorStyles.inject()
        val textAreaRef = useRef<HTMLDivElement>()
        val saveRef = useRef<(String) -> Unit>()
        var codeMirror by useState<EditorView>()

        val currentLineCallback = useRef(props.onCursorChange)

        div {
            this.ref = textAreaRef
        }
        currentLineCallback.current = props.onCursorChange
        saveRef.current = props.onSave

        useEffect(props.customClasses, props.content, codeMirror) {
            val lastMirror = codeMirror ?: return@useEffect
            val customClasses = props.customClasses ?: emptyMap()
            // Clear all marks.
            val doc = lastMirror.state.doc
            lastMirror.dispatch(
                buildExt<TransactionSpec> {
                    effects = filterMarks.of { _, _, _ -> false }
                    changes = buildExt<`T$5`> {
                        from = 0
                        to = doc.length
                        this.insert = (props.content!!)
                    }
                }
            )
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
            lastMirror.dispatch(
                buildExt<TransactionSpec> {
                    effects = addMarks.of(marks.toTypedArray())
                }
            )
        }
        useEffect(textAreaRef) {
            fun onCursorChange(viewUpdate: ViewUpdate) {
                val pos = viewUpdate.state.selection.main.head.toInt()
                val line = viewUpdate.state.doc.lineAt(pos).number.toInt() - 1
                currentLineCallback.current?.invoke(line)
            }

            val textArea = textAreaRef.current!!
            val heightTheme = EditorView.theme(
                buildExt {
                    set(
                        "&",
                        js {
                            height = "calc(100vh - 64px)"
                            width = "calc(50hw)"
                        }
                    )
                    set(".cm-scroller", js { overflow = "auto" })
                }
            )
            val editorState = EditorState.create(
                buildExt {
                    this.doc = props.content
                    this.extensions = arrayOf(
                        oneDark,
                        vim(),
                        basicSetup,
                        StreamLanguage.define(kotlin),
                        markField,
                        EditorView.lineWrapping,
                        EditorView.updateListener.of(::onCursorChange),
                        scrollPastEnd(),
                        heightTheme,
                        history()
                    )
                }
            )
            val cm = EditorView(
                buildExt {
                    this.state = editorState
                    this.parent = textArea
                }
            )

            fun onSave(cm: CodeMirror) {
                saveRef.current?.invoke(cm.getValue())
            }
            CodeMirror(cm)
            CodeMirror.commands.asDynamic().save = ::onSave
            codeMirror = cm
            cleanup {
                cm.destroy()
            }
        }
    }
) { oldProps, newProps ->
    oldProps.content === newProps.content && (
        oldProps.customClasses?.equals(newProps.customClasses)
            ?: true
        )
}

fun Text.asString(): String {
    return (0..lines.toInt()).joinToString("\n") { line(it + 1).text }
}
