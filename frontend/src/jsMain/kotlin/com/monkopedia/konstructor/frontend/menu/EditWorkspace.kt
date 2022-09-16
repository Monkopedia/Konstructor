package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.frontend.utils.useCollected
import mui.material.Button
import mui.material.ButtonColor.primary
import mui.material.ButtonColor.secondary
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogContent
import mui.material.DialogTitle
import mui.material.FormControlVariant.outlined
import mui.material.TextField
import react.FC
import react.dom.onChange
import react.useRef

val editWorkspaceDialog = FC<DialogMenusProps> { props ->
    val dialogOpen = props.dialogModel.editWorkspaceOpen.useCollected(false)
    val currentName = props.dialogModel.currentName.useCollected()
    val lastText = useRef<String>()
    Dialog {
        open = dialogOpen
        onClose = { _, _ ->
            props.dialogModel.cancel()
        }
        DialogTitle {
            +"Change workspace name"
        }
        DialogContent {
            TextField {
                +"New workspace name"
                defaultValue = currentName
                variant = outlined
                onChange = { e ->
                    lastText.current = e.target.asDynamic().value.toString()
                }
            }
        }
        DialogActions {
            Button {
                +"Delete"
                color = secondary
                onClick = {
                    props.dialogModel.delete()
                }
            }
            Button {
                +"Cancel"
                color = secondary
                onClick = {
                    props.dialogModel.cancel()
                }
            }
            Button {
                +"Save"
                color = primary
                onClick = {
                    val lastTextInput = lastText.current
                    props.dialogModel.updateWorkspaceName(lastTextInput)
                }
            }
        }
    }
}
