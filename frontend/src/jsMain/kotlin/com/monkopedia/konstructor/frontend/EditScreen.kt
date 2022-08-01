package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.TaskMessage
import csstype.AlignContent
import csstype.Color
import csstype.Display
import csstype.JustifyContent
import csstype.Position
import csstype.integer
import csstype.pct
import csstype.px
import emotion.react.css
import io.ktor.utils.io.ByteReadChannel
import kotlinext.js.js
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.background
import mui.material.Card
import mui.material.CircularProgress
import mui.material.Typography
import org.w3c.dom.HTMLTextAreaElement
import react.FC
import react.Props
import react.RefCallback
import react.createRef
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.textarea
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState
import styled.StyleSheet
import styled.getClassSelector

external interface KonstructionEditorProps : Props {
    var konstruction: Konstruction?
    var konstructionService: KonstructionService?
}

data class KonstructionEditorState(
    var isSaving: Boolean? = null,
    var isLoading: Boolean? = null,
    var currentText: String? = null,
    var currentKonstruction: Konstruction? = null
)

val LoadingUi = FC<KonstructionEditorProps> {
    div {
        css {
            display = Display.flex
            alignContent = AlignContent.center
            justifyContent = JustifyContent.center
        }
        CircularProgress {
        }
    }
}

val KonstructionEditor = FC<KonstructionEditorProps> { props ->
    var state by useState(KonstructionEditorState())
    if (state.isLoading == true) {
        LoadingUi()
        return@FC
    }
    if (state.currentKonstruction != props.konstruction) {
        GlobalScope.launch {
            state = state.copy(
                isLoading = true,
                currentKonstruction = props.konstruction
            )
            val content = props.konstructionService?.fetch(Unit)?.readRemaining()?.readText()
            state = state.copy(
                currentKonstruction = props.konstruction,
                currentText = content,
                isLoading = false
            )
        }
        LoadingUi()
        return@FC
    }
    fun onSave(content: String?) {
        GlobalScope.launch {
            state = state.copy(
                isSaving = true
            )
            props.konstructionService?.set(ByteReadChannel((content ?: "").encodeToByteArray()))
            state = state.copy(
                isSaving = false,
                currentText = content
            )
        }
    }
    if (state.currentKonstruction != null) {
        CodeMirrorScreen {
            id = "${state.currentKonstruction?.workspaceId}_${state.currentKonstruction?.id}"
            content = state.currentText
            onSave = ::onSave
            messages = listOf(
                TaskMessage("Hello there", 1)
            )
        }
    }
}

external interface CodeMirrorProps : Props {
    var content: String?
    var id: String?
    var onSave: ((String?) -> Unit)?
    var messages: List<TaskMessage>?
}

data class CodeMirrorState(
    var lastId: String? = null,
    var lastMirror: CodeMirror? = null,
    var currentMessage: String? = null
)

private object MirrorStyles : StyleSheet("mirror", isStatic = true) {
    val errorLineBackground by css {
        background = "#ff000033"
    }
}

val CodeMirrorScreen = FC<CodeMirrorProps> { props ->
    var state by useState(CodeMirrorState())

    val textAreaRef = useRef<HTMLTextAreaElement>()

    MirrorStyles.errorLineBackground
    MirrorStyles.inject()
    textarea {
        this.defaultValue = props.content.toString()
        css {
        }
        this.ref = textAreaRef
    }
    val message = state.currentMessage
    if (message != null) {
        Card {
            css {
                position = Position.absolute
                background = Color(invertedTheme.palette.background.paper)
                left = 50.pct
                right = 0.px
                bottom = 0.px
                zIndex = integer(3)
            }
            Typography {
                +message
            }
        }
    }

    fun onSave() {
        println("Saving ${state.lastMirror?.getDoc()?.getValue()}")
        props.onSave?.invoke(state.lastMirror?.getDoc()?.getValue())
    }

    val onSave: () -> Unit = ::onSave
    CodeMirror.commands.save = onSave

    fun onCursorChange(codeMirror: CodeMirror) {
        val position = codeMirror.getDoc().getCursor()
        println("Cursor change ${position.line} ${position.ch}")
        console.log(position)
        val message = props.messages?.find {
            it.line == position.line
        }?.message
        if (message != state.currentMessage) {
            state = state.copy(
                currentMessage = message
            )
        }
    }

    fun updateMirror(lastMirror: CodeMirror, props: CodeMirrorProps) {
        // Clear all marks.
        val errorClass = MirrorStyles.getClassSelector { it::errorLineBackground }.trimStart('.')
        val doc = lastMirror.getDoc()
        doc.eachLine {
            doc.removeLineClass(it, "background", errorClass)
        }
        doc.setValue(props.content.toString())
        for (message in props.messages ?: emptyList()) {
            val line = message.line ?: continue
            doc.addLineClass(line, "background", errorClass)
        }
        onCursorChange(lastMirror)
        state = state.copy(
            lastId = props.id,
            currentMessage = null
        )
    }
    state.lastMirror?.let { cm ->
        if (state.lastId != props.id) {
            updateMirror(cm, props)
        }
    }

    useEffect(textAreaRef) {
        val cm = CodeMirror.fromTextArea(
            textAreaRef.current!!,
            js {
                mode = "text/x-kotlin"
                keyMap = "vim"
                lineNumbers = true
                indentUnit = 4
                this.theme = "darcula"
                this.showCorsorWhenSelecting = true
            }
        )
        OnCursorActivity.addListener(cm, ::onCursorChange)
        updateMirror(cm, props)
        state = state.copy(
            lastMirror = cm
        )
        cleanup {
            state.lastMirror?.toTextArea()
        }
    }
}
