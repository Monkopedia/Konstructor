package com.monkopedia.konstructor.frontend.editor

import emotion.react.css
import kotlinx.css.background
import org.w3c.dom.HTMLTextAreaElement
import react.FC
import react.MutableRefObject
import react.Props
import react.dom.html.ReactHTML
import react.memo
import react.useEffect
import react.useRef
import react.useRefState
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

fun CodeMirror.cursorLine(): Int {
    val position = getDoc().getCursor()
    val line = position.line
    return line
}

val errorClass by lazy { MirrorStyles.getClassSelector { it::errorLineBackground }.trimStart('.') }

val CodeMirrorScreen = memo(
    FC<CodeMirrorProps> { props ->
        MirrorStyles.errorLineBackground
        MirrorStyles.inject()
        val textAreaRef = useRef<HTMLTextAreaElement>()
        val saveRef = useRef<(String) -> Unit>()
        var codeMirror by useState<CodeMirror>()

        val currentLineCallback = useRef(props.onCursorChange)

        ReactHTML.textarea {
            this.defaultValue = props.content.toString()
            css {
            }
            this.ref = textAreaRef
        }
        currentLineCallback.current = props.onCursorChange
        saveRef.current = props.onSave

        useEffect(props.customClasses, props.contentKey, codeMirror) {
            val lastMirror = codeMirror ?: return@useEffect
            val customClasses = props.customClasses ?: emptyMap()
            println("Updating classes for $customClasses")
            // Clear all marks.
            val doc = lastMirror.getDoc()
            doc.eachLine { line ->
                customClasses.keys.forEach {
                    doc.removeLineClass(line, "background", it)
                }
            }
            doc.setValue(props.content!!)
            for ((cls, lines) in customClasses) {
                for (line in lines) {
                    doc.addLineClass(line, "background", cls)
                }
            }
            currentLineCallback.current?.invoke(lastMirror.cursorLine())
        }
        useEffect(textAreaRef) {
            fun onCursorChange(codeMirror: CodeMirror) {
                currentLineCallback.current?.invoke(codeMirror.cursorLine())
            }

            val cm = CodeMirror.fromTextArea(
                textAreaRef.current!!,
                kotlinext.js.js {
                    mode = "text/x-kotlin"
                    keyMap = "vim"
                    lineNumbers = true
                    indentUnit = 4
                    this.theme = "darcula"
                    this.showCorsorWhenSelecting = true
                }
            )
            fun onSave() {
                saveRef.current?.invoke(cm.getDoc().getValue())
            }
            CodeMirror.commands.save = ::onSave
            OnCursorActivity.addListener(cm, ::onCursorChange)
            codeMirror = cm
            cleanup {
                cm.toTextArea()
            }
        }
    }
) { oldProps, newProps ->
    oldProps.contentKey == newProps.contentKey && (
        oldProps.customClasses?.equals(newProps.customClasses)
            ?: true
        )
}
