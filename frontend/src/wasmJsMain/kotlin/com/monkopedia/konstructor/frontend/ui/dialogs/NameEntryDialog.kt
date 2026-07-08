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
import androidx.compose.ui.Modifier

/**
 * A shared name-entry dialog used by the create/edit workspace and konstruction
 * flows. It renders an [AlertDialog] with a single "Name" [OutlinedTextField]
 * and a confirm action that only fires when the entered name is non-blank.
 *
 * When [onDelete] is non-null the confirm row also shows a "Delete" button on
 * the leading edge (the edit variants), otherwise a standard confirm/dismiss
 * button layout is used (the create variants).
 *
 * @param title dialog title text.
 * @param onConfirm invoked with the trimmed-of-blank-check name on confirm.
 * @param onDismiss invoked on cancel / outside-dismiss.
 * @param initialName initial value of the text field.
 * @param confirmLabel label of the confirm button.
 * @param onDelete when non-null, shows a "Delete" button that invokes this.
 */
@Composable
fun NameEntryDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    confirmLabel: String = "Set",
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }

    val textField: @Composable () -> Unit = {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true
        )
    }
    val confirm: () -> Unit = {
        if (name.isNotBlank()) {
            onConfirm(name)
        }
    }

    if (onDelete == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = textField,
            confirmButton = {
                TextButton(onClick = confirm) {
                    Text(confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = textField,
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDelete) {
                        Text("Delete")
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        TextButton(onClick = confirm) {
                            Text(confirmLabel)
                        }
                    }
                }
            }
        )
    }
}
