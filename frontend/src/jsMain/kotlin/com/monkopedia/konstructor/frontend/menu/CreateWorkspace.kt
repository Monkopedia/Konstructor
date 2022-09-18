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

val createWorkspaceDialog = FC<DialogMenusProps> { props ->
    val dialogOpen = props.dialogModel.createWorkspaceOpen.useCollected(false)
    val lastText = useRef<String>()
    Dialog {
        open = dialogOpen
        onClose = { _, _ ->
            props.dialogModel.cancel()
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
                    props.dialogModel.cancel()
                }
            }
            Button {
                +"Set"
                color = primary
                onClick = {
                    val lastTextInput = lastText.current
                    props.dialogModel.createWorkspace(lastTextInput)
                }
            }
        }
    }
}
