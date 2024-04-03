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

import com.monkopedia.konstructor.frontend.invertedTheme
import com.monkopedia.konstructor.frontend.model.KonstructionModel
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.COMPILING
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.EXECUTING
import com.monkopedia.konstructor.frontend.model.KonstructionModel.State.LOADING
import com.monkopedia.konstructor.frontend.utils.useCollected
import dukat.codemirror.state.EditorState
import dukat.codemirror.view.EditorView
import emotion.react.css
import mui.material.Card
import mui.material.CircularProgress
import mui.material.Typography
import mui.system.sx
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.memo
import web.cssom.AlignContent
import web.cssom.Color
import web.cssom.Display
import web.cssom.JustifyContent
import web.cssom.Position
import web.cssom.integer
import web.cssom.pct
import web.cssom.px

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
    val currentMessage = props.konstructionModel.currentMessage.useCollected(null)
    val currentText = props.konstructionModel.currentText.useCollected()
    if (state == LOADING || currentText == null) {
        LoadingUi()
        return@FC
    }
    EditorScreen {
//        content = currentText
        this.setViewAvailable = props.konstructionModel.setViewAvailable
        this.editorState = props.konstructionModel.editorState
        this.onSave = props.konstructionModel.onSave
        this.currentMessage = currentMessage
        this.state = state
    }
}

external interface EditorScreenProps : Props {
    var setViewAvailable: (EditorView, Boolean) -> Unit
    var editorState: EditorState
    var target: String
    var onSave: ((String?) -> Unit)?
    var state: State
    var currentMessage: String?
}

val EditorScreen = memo(
    FC<EditorScreenProps> { props ->
        CodeMirrorScreen {
            this.setViewAvailable = props.setViewAvailable
            this.editorState = props.editorState
            this.target = props.target
            this.onSave = props.onSave
        }

        if (props.currentMessage != null || props.state == COMPILING || props.state == EXECUTING) {
            MessageComponent {
                message = props.currentMessage
                this.state = props.state
            }
        }
    }
) { oldProps, newProps ->
    oldProps.editorState === newProps.editorState &&
        oldProps.target == newProps.target &&
        oldProps.currentMessage == newProps.currentMessage &&
        oldProps.state == newProps.state
}

external interface MessageProps : Props {
    var message: String?
    var state: State
}

val MessageComponent = memo(
    FC<MessageProps> { props ->
        val message = when (props.state) {
            COMPILING -> "Compiling..."
            EXECUTING -> "Executing..."
            else -> props.message!!
        }
        Card {
            sx {
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
