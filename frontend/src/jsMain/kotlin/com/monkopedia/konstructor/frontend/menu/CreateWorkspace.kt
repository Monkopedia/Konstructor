package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.common.Space
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
import react.dom.onChange

val createWorkspaceButton = FC<SubComponentProps> { props ->
    IconButton {
        Add()
        onClick = {
            props.editContext.state = props.editContext.state.copy(
                createWorkspaceDialog = true
            )
        }
    }
    Dialog {
        open = props.editContext.state.createWorkspaceDialog ?: false
        onClose = { _, _ ->
            props.editContext.closeAllDialogs()
        }
        DialogTitle {
            +"Enter new workspace name"
        }
        DialogContent {
            TextField {
                +"New workspace name"
                variant = outlined
                onChange = { e ->
                    props.editContext.state = props.editContext.state.copy(
                        lastTextInput = e.target.asDynamic().value.toString()
                    )
                }
            }
        }
        DialogActions {
            Button {
                +"Cancel"
                color = secondary
                onClick = {
                    props.editContext.closeAllDialogs()
                }
            }
            Button {
                +"Set"
                color = primary
                onClick = {
                    val lastTextInput = props.editContext.state.lastTextInput
                    props.menuProps.workManager.doWork {
                        if (lastTextInput.isNullOrEmpty()) {
                            return@doWork
                        }
                        val newWorkspace = props.menuProps.service.create(
                            Space(id = "", name = lastTextInput)
                        )
                        val updatedWorkspaces = props.menuProps.workspaces?.plus(newWorkspace)
                        props.menuProps.onWorkspacesChanged?.invoke(updatedWorkspaces)
                        props.menuProps.onWorkspaceSelected?.invoke(newWorkspace.id)
                        props.editContext.closeAllDialogs()
                    }
                }
            }
        }
    }
}
