package com.monkopedia.konstructor.frontend.editor

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.frontend.invertedTheme
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
import io.ktor.utils.io.printStack
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.background
import mui.material.Card
import mui.material.CircularProgress
import mui.material.Typography
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.memo
import react.useMemo
import react.useState

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
            val content = props.konstructionService?.fetch()
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
            props.konstructionService?.set(content ?: "")
            state = state.copy(
                isSaving = false,
                currentText = content
            )
        }
    }
    if (state.currentKonstruction != null) {
        EditorScreen {
            id = "${state.currentKonstruction?.workspaceId}_${state.currentKonstruction?.id}"
            content = state.currentText
            onSave = ::onSave
            messages = listOf(
                TaskMessage("Hello there", 1)
            )
        }
    }
}

external interface EditorScreenProps : Props {
    var content: String?
    var id: String?
    var onSave: ((String?) -> Unit)?
    var messages: List<TaskMessage>?
}



val EditorScreen = memo(
    FC<EditorScreenProps> { props ->
        var currentMessage by useState<String>()
        val classes = useMemo(props.messages) {
            mapOf(
                errorClass to (props.messages?.mapNotNull { it.line } ?: emptyList())
            )
        }

        fun onCursorChange(line: Int) {
            val message = props.messages?.find {
                it.line == line
            }?.message
            currentMessage = message
        }

        CodeMirrorScreen {
            this.onCursorChange = ::onCursorChange
            this.content = props.content
            this.onSave = props.onSave
            this.contentKey = props.id
            this.customClasses = classes
        }

        if (currentMessage != null) {
            MessageComponent {
                message = currentMessage
            }
        }
    }
) { oldProps, newProps ->
    oldProps.id == newProps.id && (oldProps.messages ?: emptyList()).equals(newProps.messages)
}

external interface MessageProps : Props {
    var message: String?
}

val MessageComponent = memo(
    FC<MessageProps> { props ->
        val message = props.message!!
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
) { oldProps, newProps ->
    oldProps.message == newProps.message
}
