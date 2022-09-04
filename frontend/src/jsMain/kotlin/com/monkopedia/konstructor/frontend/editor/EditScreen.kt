package com.monkopedia.konstructor.frontend.editor

import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.frontend.invertedTheme
import com.monkopedia.konstructor.frontend.model.KonstructionModel
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.LOADING
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.AlignContent
import csstype.Color
import csstype.Display
import csstype.JustifyContent
import csstype.Position
import csstype.integer
import csstype.pct
import csstype.px
import emotion.react.css
import mui.material.Card
import mui.material.CircularProgress
import mui.material.Typography
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.memo
import react.useCallback
import react.useMemo
import react.useState

external interface KonstructionEditorProps : Props {
    var konstructionModel: KonstructionModel
    var workspaceId: String
    var konstructionId: String
}

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
    val state = props.konstructionModel.state.useCollected(LOADING)
    val messages = props.konstructionModel.messages.useCollected(emptyList())
    val currentText = props.konstructionModel.currentText.useCollected()
    if (state == LOADING || currentText == null) {
        LoadingUi()
        return@FC
    }
    EditorScreen {
        id = "${props.workspaceId}_${props.konstructionId}"
        content = currentText
        onSave = props.konstructionModel.onSave
        this.messages = messages
        this.state = state
    }
}

external interface EditorScreenProps : Props {
    var content: String?
    var id: String?
    var onSave: ((String?) -> Unit)?
    var messages: List<TaskMessage>
    var state: State
}

val EditorScreen = memo(
    FC<EditorScreenProps> { props ->
        var currentMessage by useState<String>()
        val classes = useMemo(props.messages) {
            mapOf(
                errorClass to (props.messages.mapNotNull { it.line } ?: emptyList())
            )
        }

        val onCursorChange = useCallback(props.messages) { line: Int ->
            val message = props.messages.find {
                it.line == line
            }?.message
            currentMessage = message
        }

        CodeMirrorScreen {
            this.onCursorChange = onCursorChange
            this.content = props.content
            this.onSave = props.onSave
            this.contentKey = props.id
            this.customClasses = classes
        }

        if (currentMessage != null) {
            MessageComponent {
                message = currentMessage
                this.state = props.state
            }
        }
    }
) { oldProps, newProps ->
    oldProps.id == newProps.id && oldProps.messages == newProps.messages
}

external interface MessageProps : Props {
    var message: String?
    var state: State
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
