package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.common.Konstruction
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

val createKonstructionButton = FC<SubComponentProps> { props ->
    IconButton {
        Add()
        onClick = {
            props.editContext.state = props.editContext.state.copy(
                createKonstructionDialog = true
            )
        }
    }
    Dialog {
        open = props.editContext.state.createKonstructionDialog ?: false
        onClose = { _, _ ->
            props.editContext.closeAllDialogs()
        }
        DialogTitle {
            +"Enter new konstruction name"
        }
        DialogContent {
            TextField {
                "New konstruction name"
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
                        val newKonstruction = props.menuProps.currentWorkspaceService?.create(
                            Konstruction(
                                id = "",
                                name = lastTextInput,
                                workspaceId = props.menuProps.currentWorkspace
                                    ?: error("Lost workspace")
                            )
                        ) ?: error("Missing workspace")
                        val updatedKonstructions =
                            props.menuProps.konstructions?.plus(newKonstruction)
                        props.menuProps.onKonstructionsChanged?.invoke(updatedKonstructions)
                        props.menuProps.onKonstructionSelected?.invoke(newKonstruction.id)
                        props.editContext.closeAllDialogs()
                    }
                }
            }
        }
    }
}
