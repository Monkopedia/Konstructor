/*
 * Copyright 2022 Jason Monk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.frontend.model.GlobalDialogsModel
import com.monkopedia.konstructor.frontend.utils.useCollected
import mui.material.Button
import mui.material.ButtonColor.Companion.error
import mui.material.ButtonColor.Companion.primary
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogContent
import mui.material.DialogTitle
import mui.material.Typography
import mui.material.styles.TypographyVariant.Companion.body1
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
