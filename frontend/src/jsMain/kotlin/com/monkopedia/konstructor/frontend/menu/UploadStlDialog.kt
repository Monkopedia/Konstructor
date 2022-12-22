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
import mui.material.Input
import mui.material.TextField
import mui.material.Typography
import org.w3c.files.File
import react.FC
import react.dom.onChange
import react.useRef
import react.useState

val uploadStlDialog = FC<DialogMenusProps> { props ->
    val dialogOpen = props.dialogModel.uploadStlOpen.useCollected(false)
    var state by useState<File>()
    val lastText = useRef<String>()
    Dialog {
        open = dialogOpen
        onClose = { _, _ ->
            props.dialogModel.cancel()
        }
        DialogTitle {
            +"Enter new konstruction name"
        }
        DialogContent {
            Typography {
                +(state?.name ?: "<Select file>")
            }
            Button {
                color = primary
                Input {
                    type = "file"
                    this.onChange = {
                        state = it.target.asDynamic().files[0] as File
                    }
                }
            }
        }
        DialogActions {
            Button {
                +"Cancel"
                color = secondary
                onClick = {
                    props.dialogModel.cancel()
                }
            }
            Button {
                +"Upload"
                color = primary
                disabled = state == null
                onClick = {
                    state!!.slice()
                    val lastTextInput = lastText.current
                    props.dialogModel.createKonstruction(lastTextInput)
                }
            }
        }
    }
}