package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.model.WorkspaceModel
import mui.icons.material.Add
import mui.material.Button
import mui.material.ButtonColor.primary
import mui.material.ButtonColor.secondary
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogContent
import mui.material.DialogTitle
import mui.material.FormControlVariant.outlined
import mui.material.IconButton
import mui.material.TextField
import react.FC
import react.Props
import react.dom.onChange
import react.useRef
import react.useState

external interface CreateKonstructionProps : Props {
    var workManager: WorkManager
    var workspaceModel: WorkspaceModel
    var onCreateWorkspace: suspend (Konstruction) -> Unit
}

val createKonstructionButton = FC<CreateKonstructionProps> { props ->
    var dialogOpen by useState(false)
    val lastText = useRef<String>()
    IconButton {
        Add()
        onClick = {
            dialogOpen = true
        }
    }
    Dialog {
        open = dialogOpen
        onClose = { _, _ ->
            dialogOpen = false
        }
        DialogTitle {
            +"Enter new konstruction name"
        }
        DialogContent {
            TextField {
                "New konstruction name"
                variant = outlined
                onChange = { e ->
                    lastText.current = e.target.asDynamic().value.toString()
                }
            }
        }
        DialogActions {
            Button {
                +"Cancel"
                color = secondary
                onClick = {
                    dialogOpen = false
                }
            }
            Button {
                +"Set"
                color = primary
                onClick = {
                    val lastTextInput = lastText.current
                    props.workManager.doWork {
                        if (lastTextInput.isNullOrEmpty()) {
                            return@doWork
                        }
                        props.onCreateWorkspace(
                            Konstruction(
                                id = "",
                                name = lastTextInput,
                                workspaceId = props.workspaceModel.workspaceId
                            )
                        )
                        dialogOpen = false
                    }
                }
            }
        }
    }
}
