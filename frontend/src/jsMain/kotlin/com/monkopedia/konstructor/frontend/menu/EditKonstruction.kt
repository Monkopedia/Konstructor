package com.monkopedia.konstructor.frontend.menu

import mui.icons.material.Edit
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


val editKonstructionButton = FC<SubComponentProps> { props ->
    IconButton {
        Edit()
        onClick = {
            props.editContext.state = props.editContext.state.copy(
                editKonstructionDialog = true
            )
        }
    }
    Dialog {
        open = props.editContext.state.editKonstructionDialog ?: false
        onClose = { _, _ ->
            props.editContext.closeAllDialogs()
        }
        DialogTitle {
            +"Change konstruction name"
        }
        DialogContent {
            TextField {
                +"New konstruction name"
                defaultValue = props.currentSpace?.name
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
                +"Create"
                color = primary
                onClick = {
                    val lastTextInput = props.editContext.state.lastTextInput
                    props.menuProps.workManager.doWork {
                        if (lastTextInput == null || lastTextInput == props.currentSpace?.name) {
                            props.editContext.closeAllDialogs()
                            return@doWork
                        }
                        props.menuProps.currentKonstructionService?.setName(lastTextInput)
                        val updatedKonstructions = props.menuProps.konstructions?.map {
                            if (it.id == props.menuProps.currentKonstruction) {
                                it.copy(name = lastTextInput)
                            } else it
                        }
                        props.menuProps.onKonstructionsChanged?.invoke(updatedKonstructions)
                        props.editContext.closeAllDialogs()
                    }
                }
            }
        }
    }
}
