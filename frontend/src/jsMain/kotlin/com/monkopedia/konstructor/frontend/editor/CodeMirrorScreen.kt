package com.monkopedia.konstructor.frontend.editor

import codemirror.themeonedark.oneDark
import com.monkopedia.konstructor.frontend.utils.buildExt
import dukat.codemirror.basicSetup
import dukat.codemirror.language.StreamLanguage
import dukat.codemirror.legacymodes.kotlin
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.Text
import dukat.codemirror.state.TransactionSpec
import dukat.codemirror.state.`T$5`
import dukat.codemirror.view.Decoration
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.MouseSelectionStyle
import dukat.codemirror.view.ViewUpdate
import dukat.codemirror.view.scrollPastEnd
import dukat.codemirror.vim.CodeMirror
import dukat.codemirror.vim.vim
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

private object MirrorStyles : StyleSheet("mirror", isStatic = true) {
    val errorLineBackground by css {
        background = "#ff000033"
    }
}

external interface CodeMirrorProps : Props {
    var content: String?
    var contentKey: String?
    var onCursorChange: ((Int) -> Unit)?
    var onSave: ((String) -> Unit)?
    var customClasses: Map<String, List<Int>>?
}

//fun CodeMirror.cursorLine(): Int {
//    val position = getDoc().getCursor()
//    val line = position.line
//    return line
//}

val errorClass by lazy { MirrorStyles.getClassSelector { it::errorLineBackground }.trimStart('.') }

val CodeMirrorScreen = memo(
    FC<CodeMirrorProps> { props ->
//        ReactCodeMirror {
//            this.value = props.content
//            this.theme = "light"
//        }
        MirrorStyles.errorLineBackground
        MirrorStyles.inject()
        val textAreaRef = useRef<HTMLDivElement>()
        val saveRef = useRef<(String) -> Unit>()
        var codeMirror by useState<EditorView>()
        var state by useState<EditorState>()

        val currentLineCallback = useRef(props.onCursorChange)

        div {
            this.ref = textAreaRef
        }
//        ReactHTML.textarea {
//            println("Default value ${props.content}")
//            this.defaultValue = props.content.toString()
//            css {
//            }
//            this.ref = textAreaRef
//        }
        currentLineCallback.current = props.onCursorChange
        saveRef.current = props.onSave

        useEffect(props.customClasses, props.contentKey, codeMirror, state) {
            val lastMirror = codeMirror ?: return@useEffect
            val customClasses = props.customClasses ?: emptyMap()
            // Clear all marks.
            val doc = state?.doc ?: return@useEffect
//            doc.eachLine { line ->
//                customClasses.keys.forEach {
//                    doc.removeLineClass(line, "background", it)
//                }
//            }
            println("Expected content ${props.content}")
            lastMirror.dispatch(buildExt<TransactionSpec> {
                effects = filterMarks.of { _, _, _ -> false }
                changes = buildExt<`T$5`> {
                    from = 0
                    to = state?.doc?.length
                    this.insert = (props.content!!)
                }
            })
            val marks = customClasses.entries.flatMap { (key, lines) ->
                lines.filter {
                    (it + 1) <= doc.lines.toInt()
                }.map {
                    doc.line(it + 1).let { lineInfo ->
                        Decoration.mark(buildExt {
                            this.`class` = key
                        }).range(lineInfo.from, lineInfo.to)
                    }
                }
            }
            lastMirror.dispatch(buildExt<TransactionSpec> {
                effects = addMarks.of(marks.toTypedArray())
            })
//            for ((cls, lines) in customClasses) {
//                for (line in lines) {
//                    doc.addLineClass(line, "background", cls)
//                }
//            }
//            currentLineCallback.current?.invoke(lastMirror.cursorLine())
        }
        useEffect(textAreaRef) {
            fun onCursorChange(viewUpdate: ViewUpdate) {
                val pos = viewUpdate.state.selection.main.head.toInt()
                val line = viewUpdate.state.doc.lineAt(pos).number.toInt() - 1
                currentLineCallback.current?.invoke(line)
            }

            val textArea = textAreaRef.current!!
            println("Attach ${textArea.hashCode()}")
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
                        scrollPastEnd()
                    )
                }
            )
            val cm = EditorView(buildExt {
                this.state = editorState
                this.parent = textArea
            })

            //            val cm = CodeMirror.fromTextArea(
//                textArea,
//                kotlinext.js.js {
//                    mode = "text/x-kotlin"
//                    keyMap = "vim"
//                    lineNumbers = true
//                    indentUnit = 4
//                    this.theme = "darcula"
//                    this.showCorsorWhenSelecting = true
//                }
//            )
            fun onSave(cm: CodeMirror) {
                saveRef.current?.invoke(cm.getValue().also {
                    println("Saving [$it]")
                })
            }
            val vimCompatApi = CodeMirror(cm)
            CodeMirror.commands.asDynamic().save = ::onSave
//            OnCursorActivity.addListener(cm, ::onCursorChange)
            codeMirror = cm
            state = editorState
            cleanup {
                println("Cleanup ${textArea.hashCode()}")
//                cm.toTextArea()
            }
        }
    }
) { oldProps, newProps ->
    oldProps.contentKey == newProps.contentKey && (
        oldProps.customClasses?.equals(newProps.customClasses)
            ?: true
        )
}

fun Text.asString(): String {
    return (0..lines.toInt()).joinToString("\n") { line(it + 1).text }
}
