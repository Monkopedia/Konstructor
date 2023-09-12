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

import com.monkopedia.konstructor.frontend.utils.buildExt
import com.monkopedia.konstructor.frontend.utils.useCollected
import mui.material.Button
import mui.material.ButtonColor.Companion.primary
import mui.material.ButtonColor.Companion.secondary
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogContent
import mui.material.DialogTitle
import mui.material.Input
import mui.material.Typography
import org.w3c.files.File
import react.FC
import react.useState

val uploadStlDialog = FC<DialogMenusProps> { props ->
    val dialogOpen = props.dialogModel.uploadStlOpen.useCollected(false)
    var state by useState<File>()
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
                    this.autoFocus
                    inputProps = buildExt {
                        asDynamic().accept = ".stl"
                    }
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
                    props.dialogModel.uploadFile(state!!)
                }
            }
        }
    }
}
