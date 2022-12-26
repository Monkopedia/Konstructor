package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.frontend.model.GlobalDialogsModel
import com.monkopedia.konstructor.frontend.utils.useCollected
import mui.material.Button
import mui.material.ButtonColor.error
import mui.material.ButtonColor.primary
import mui.material.ButtonColor.secondary
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogContent
import mui.material.DialogTitle
import mui.material.Typography
import mui.material.styles.TypographyVariant.body1
import react.FC
import react.Props
import react.memo

external interface GlobalDialogsProps : Props {
    var dialogModel: GlobalDialogsModel
}

val GlobalDialogs = memo(
    FC<GlobalDialogsProps> { props ->
        val pendingPresent = props.dialogModel.hasConflictingState.useCollected(false)

        Dialog {
            open = pendingPresent
            onClose = { _, _ -> }
            DialogTitle {
                +"You are out of sync with server state"
            }
            DialogContent {
                Typography {
                    this.variant = body1
                    +"Do you want to discard local changes or overwrite server content?"
                }
            }
            DialogActions {
                Button {
                    +"Discard"
                    color = primary
                    onClick = {
                        props.dialogModel.discardState()
                    }
                }
                Button {
                    +"Overwrite"
                    color = error
                    onClick = {
                        props.dialogModel.overwriteState()
                    }
                }
            }
        }
    }
)
