package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.frontend.WorkManager
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
import react.Props
import react.dom.onChange
import react.useRef
import react.useState

external interface EditKonstructionProps : Props {
    var workManager: WorkManager
    var currentName: String
    var onUpdateName: suspend (String) -> Unit
}

val editKonstructionButton = FC<EditKonstructionProps> { props ->
    var dialogOpen by useState(false)
    val lastText = useRef<String>()
    IconButton {
        Edit()
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
            +"Change konstruction name"
        }
        DialogContent {
            TextField {
                +"New konstruction name"
                defaultValue = props.currentName
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
                +"Create"
                color = primary
                onClick = {
                    val lastTextInput = lastText.current
                    props.workManager.doWork {
                        if (lastTextInput == null || lastTextInput == props.currentName) {
                            dialogOpen = false
                            return@doWork
                        }

                        props.onUpdateName(lastTextInput)
                        dialogOpen = false
                    }
                }
            }
        }
    }
}
