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
package com.monkopedia.konstructor.frontend.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.viewmodel.NavigationDialogViewModel
import org.koin.compose.koinInject

@Composable
fun EditWorkspaceDialog(space: Space) {
    val dialogVm = koinInject<NavigationDialogViewModel>()
    var name by remember { mutableStateOf(space.name) }

    AlertDialog(
        onDismissRequest = { dialogVm.hideEditWorkspaceDialog() },
        title = { Text("Change workspace name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { dialogVm.deleteWorkspace(space) }
                ) {
                    Text("Delete")
                }
                Row {
                    TextButton(onClick = { dialogVm.hideEditWorkspaceDialog() }) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                dialogVm.renameWorkspace(space.id, name)
                            }
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    )
}
