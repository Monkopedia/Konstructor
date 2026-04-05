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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.monkopedia.konstructor.frontend.viewmodel.NavigationDialogViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SyncConflictDialog() {
    val dialogVm = koinViewModel<NavigationDialogViewModel>()

    AlertDialog(
        onDismissRequest = { dialogVm.hideSyncConflictDialog() },
        title = { Text("Out of Sync") },
        text = { Text("You are out of sync with server state.") },
        confirmButton = {
            TextButton(onClick = {
                // Overwrite server with local content
                dialogVm.hideSyncConflictDialog()
            }) {
                Text("Overwrite")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                // Discard local changes and reload from server
                dialogVm.hideSyncConflictDialog()
            }) {
                Text("Discard")
            }
        }
    )
}
