package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.model.WorkspaceModel
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
import react.Props
import react.dom.onChange
import react.useRef

val createKonstructionDialog = FC<DialogMenusProps> { props ->
    val dialogOpen = props.dialogModel.createKonstructionOpen.useCollected(false)
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
                    props.dialogModel.cancel()
                }
            }
            Button {
                +"Set"
                color = primary
                onClick = {
                    val lastTextInput = lastText.current
                    props.dialogModel.createKonstruction(lastTextInput)
                }
            }
        }
    }
}
