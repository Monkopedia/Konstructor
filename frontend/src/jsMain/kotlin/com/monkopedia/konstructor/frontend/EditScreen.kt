package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.card.mCard
import com.ccfraser.muirwik.components.mCircularProgress
import com.ccfraser.muirwik.components.mTypography
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.TaskMessage
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinext.js.js
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.Align
import kotlinx.css.Display
import kotlinx.css.JustifyContent
import kotlinx.css.Position
import kotlinx.css.alignContent
import kotlinx.css.background
import kotlinx.css.bottom
import kotlinx.css.display
import kotlinx.css.justifyContent
import kotlinx.css.left
import kotlinx.css.pct
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.right
import kotlinx.css.zIndex
import kotlinx.html.TEXTAREA
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.attrs
import react.dom.defaultValue
import react.setState
import styled.StyleSheet
import styled.css
import styled.getClassSelector
import styled.styledDiv
import styled.styledTextarea

external interface KonstructionEditorProps : Props {
    var konstruction: Konstruction?
    var konstructionService: KonstructionService?
}

external interface KonstructionEditorState : State {
    var isSaving: Boolean?
    var isLoading: Boolean?
    var currentText: String?
    var currentKonstruction: Konstruction?
}

class KonstructionEditor : RComponent<KonstructionEditorProps, KonstructionEditorState>() {
    override fun RBuilder.render() {
        if (state.isLoading == true) {
            return loadingUi()
        }
        if (state.currentKonstruction != props.konstruction) {
            GlobalScope.launch {
                setState {
                    isLoading = true
                    currentKonstruction = props.konstruction
                }
                val content = props.konstructionService?.fetch(Unit)?.readRemaining()?.readText()
                setState {
                    currentText = content
                    isLoading = false
                }
            }
            return loadingUi()
        }
        if (state.currentKonstruction != null) {
            codeMirror()
        }
    }

    private fun RBuilder.loadingUi() {
        styledDiv {
            css {
                display = Display.flex
                alignContent = Align.center
                justifyContent = JustifyContent.center
            }
            mCircularProgress {
            }
        }
    }

    fun RBuilder.codeMirror() {
        child(CodeMirrorScreen::class) {
            attrs {
                id = "${state.currentKonstruction?.workspaceId}_${state.currentKonstruction?.id}"
                content = state.currentText
                onSave = this@KonstructionEditor::onSave
                messages = listOf(
                    TaskMessage("Hello there", 1)
                )
            }
        }
    }

    private fun onSave(content: String?) {
        GlobalScope.launch {
            setState {
                isSaving = true
            }
            props.konstructionService?.set(ByteReadChannel((content ?: "").encodeToByteArray()))
            setState {
                isSaving = false
                this.currentText = content
            }
        }
    }
}

external interface CodeMirrorProps : Props {
    var content: String?
    var id: String?
    var onSave: ((String?) -> Unit)?
    var messages: List<TaskMessage>?
}

external interface CodeMirrorState : State {
    var lastId: String?
    var lastMirror: CodeMirror?
    var currentMessage: String?
}

class CodeMirrorScreen(props: CodeMirrorProps) :
    RComponent<CodeMirrorProps, CodeMirrorState>(props) {
    val textAreaRef = createRef<TEXTAREA>()

    private object MirrorStyles : StyleSheet("mirror", isStatic = true) {
        val errorLineBackground by css {
            background = "#ff000033"
        }
    }

    override fun RBuilder.render() {
        MirrorStyles.errorLineBackground
        MirrorStyles.inject()
        styledTextarea {
            attrs {
                this.defaultValue = props.content.toString()
            }
            css {
            }
            this.ref = textAreaRef
        }
        val message = state.currentMessage
        if (message != null) {
            mCard {
                css {
                    position = Position.absolute
                    background = invertedTheme.palette.background.paper
                    left = 50.pct
                    right = 0.px
                    bottom = 0.px
                    zIndex = 3
                }
                mTypography(message)
            }
        }
    }

    override fun componentWillUnmount() {
        state.lastMirror?.toTextArea()
    }

    override fun componentWillReceiveProps(nextProps: CodeMirrorProps) {
        val cm = state.lastMirror ?: return
        if (state.lastId != nextProps.id) {
            updateMirror(cm, nextProps)
        }
    }

    override fun componentDidMount() {
        val onSave: () -> Unit = this::onSave
        CodeMirror.commands.save = onSave
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
        OnCursorActivity.addListener(cm, this::onCursorChange)
        updateMirror(cm, props)
        setState {
            this.lastMirror = cm
        }
    }

    private fun updateMirror(lastMirror: CodeMirror, props: CodeMirrorProps) {
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
        setState {
            lastId = props.id
            currentMessage = null
        }
    }

    private fun onCursorChange(codeMirror: CodeMirror) {
        val position = codeMirror.getDoc().getCursor()
        println("Cursor change ${position.line} ${position.ch}")
        console.log(position)
        val message = props.messages?.find {
            it.line == position.line
        }?.message
        if (message != state.currentMessage) {
            setState {
                currentMessage = message
            }
        }
    }

    private fun onSave() {
        props.onSave?.invoke(state.lastMirror?.getDoc()?.getValue() as? String)
    }
}
