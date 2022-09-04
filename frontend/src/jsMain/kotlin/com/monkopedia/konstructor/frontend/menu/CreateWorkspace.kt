package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.WorkManager
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

external interface CreateWorkspaceProps : Props {
    var workManager: WorkManager
    var onCreateWorkspace: suspend (Space) -> Unit
}

val createWorkspaceButton = FC<CreateWorkspaceProps> { props ->
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
            +"Enter new workspace name"
        }
        DialogContent {
            TextField {
                +"New workspace name"
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
                            Space(id = "", name = lastTextInput)
                        )
                        dialogOpen = false
                    }
                }
            }
        }
    }
}
