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
package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionType.STL
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel.Dialogs.CREATE_KONSTRUCTION
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel.Dialogs.CREATE_WORKSPACE
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel.Dialogs.EDIT_KONSTRUCTION
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel.Dialogs.EDIT_WORKSPACE
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel.Dialogs.NONE
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel.Dialogs.UPLOAD_STL
import com.monkopedia.konstructor.frontend.utils.asArrayBuffer
import com.monkopedia.ksrpc.use
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.khronos.webgl.DataView
import org.w3c.files.File

class NavigationDialogModel(
    private val coroutineScope: CoroutineScope,
    val workManager: WorkManager,
) : Closeable {
    enum class Dialogs {
        NONE,
        WORKING,
        CREATE_WORKSPACE,
        CREATE_KONSTRUCTION,
        EDIT_WORKSPACE,
        EDIT_KONSTRUCTION,
        UPLOAD_STL,
    }

    data class DialogState(
        val dialog: Dialogs,
        val targetWorkspace: String? = null,
        val targetKonstruction: String? = null,
        val currentName: String? = null,
    )

    private val mutableDialog = MutableStateFlow(DialogState(NONE))
    val dialog: Flow<DialogState> = mutableDialog

    val createWorkspaceOpen = dialog.map { it.dialog == CREATE_WORKSPACE }
    val createKonstructionOpen = dialog.map { it.dialog == CREATE_KONSTRUCTION }
    val editWorkspaceOpen = dialog.map { it.dialog == EDIT_WORKSPACE }
    val editKonstructionOpen = dialog.map { it.dialog == EDIT_KONSTRUCTION }
    val uploadStlOpen = dialog.map { it.dialog == UPLOAD_STL }
    val targetWorkspace = dialog.map { it.targetWorkspace }
    val targetKonstruction = dialog.map { it.targetKonstruction }
    val currentName = dialog.map { it.currentName }

    fun showCreateWorkspace() {
        mutableDialog.value = DialogState(CREATE_WORKSPACE)
    }

    fun showCreateKonstruction(workspaceId: String) {
        mutableDialog.value = DialogState(CREATE_KONSTRUCTION, targetWorkspace = workspaceId)
    }

    fun showUploadStl(workspaceId: String) {
        mutableDialog.value = DialogState(UPLOAD_STL, targetWorkspace = workspaceId)
    }

    fun showEditWorkspace(
        workspaceId: String,
        currentName: String,
    ) {
        mutableDialog.value =
            DialogState(EDIT_WORKSPACE, targetWorkspace = workspaceId, currentName = currentName)
    }

    fun showEditKonstruction(
        workspaceId: String,
        konstructionId: String,
        currentName: String,
    ) {
        mutableDialog.value =
            DialogState(
                EDIT_KONSTRUCTION,
                targetWorkspace = workspaceId,
                targetKonstruction = konstructionId,
                currentName = currentName,
            )
    }

    fun cancel() {
        mutableDialog.value = DialogState(NONE)
    }

    override fun close() {
        cancel()
    }

    fun createKonstruction(lastTextInput: String?) {
        if (lastTextInput.isNullOrEmpty()) return
        val dialogInfo = mutableDialog.value
        val targetWorkspace = dialogInfo.targetWorkspace ?: return
        cancel()
        workManager.doWork {
            val service = RootScope.serviceHolder.service.first()
            service.get(targetWorkspace).use { workspace ->
                workspace.create(
                    Konstruction(
                        id = "",
                        name = lastTextInput,
                        workspaceId = targetWorkspace,
                    ),
                )
            }
            WorkspaceModel.refreshAllKonstructions()
        }
    }

    fun createWorkspace(lastTextInput: String?) {
        if (lastTextInput.isNullOrEmpty()) return
        cancel()
        workManager.doWork {
            val service = RootScope.serviceHolder.service.first()
            service.create(
                Space(id = "", name = lastTextInput),
            )
            RootScope.spaceListModel.refreshWorkspaces()
        }
    }

    fun updateKonstructionName(lastTextInput: String?) {
        if (lastTextInput.isNullOrEmpty()) return
        val dialogInfo = mutableDialog.value
        val targetWorkspace = dialogInfo.targetWorkspace ?: return
        val targetKonstruction = dialogInfo.targetKonstruction ?: return
        cancel()
        workManager.doWork {
            val service = RootScope.serviceHolder.service.first()
            service.konstruction(Konstruction("", targetWorkspace, targetKonstruction)).use { ks ->
                ks.setName(lastTextInput)
            }
            WorkspaceModel.refreshAllKonstructions()
        }
    }

    fun updateWorkspaceName(lastTextInput: String?) {
        if (lastTextInput.isNullOrEmpty()) return
        val dialogInfo = mutableDialog.value
        val targetWorkspace = dialogInfo.targetWorkspace ?: return
        cancel()
        workManager.doWork {
            val service = RootScope.serviceHolder.service.first()
            service.get(targetWorkspace).use { workspace ->
                workspace.setName(lastTextInput)
            }
            RootScope.spaceListModel.refreshWorkspaces()
        }
    }

    fun uploadFile(state: File) {
        val dialogInfo = mutableDialog.value
        val targetWorkspace = dialogInfo.targetWorkspace ?: return
        cancel()
        workManager.doWork {
            val service = RootScope.serviceHolder.service.first()
            service.get(targetWorkspace).use { ks ->
                val name = state.name.replace(".stl", "")
                val newFile = ks.create(Konstruction(name, targetWorkspace, "", type = STL))
                service.konstruction(newFile).use { newFileService ->
                    coroutineScope {
                        val content = state.asArrayBuffer()
                        val channel = ByteChannel(autoFlush = true)
                        launch {
                            channel.writeFully(Memory(DataView(content)), 0, content.byteLength)
                        }
                        newFileService.setBinary(channel)
                    }
                }
            }
            WorkspaceModel.refreshAllKonstructions()
        }
    }

    fun delete() {
        when (mutableDialog.value.dialog) {
            EDIT_WORKSPACE -> {
                val dialogInfo = mutableDialog.value
                val targetWorkspace = dialogInfo.targetWorkspace ?: return
                cancel()
                workManager.doWork {
                    val service = RootScope.serviceHolder.service.first()
                    service.delete(Space(targetWorkspace, ""))
                    RootScope.spaceListModel.refreshWorkspaces()
                }
            }

            EDIT_KONSTRUCTION -> {
                val dialogInfo = mutableDialog.value
                val targetWorkspace = dialogInfo.targetWorkspace ?: return
                val targetKonstruction = dialogInfo.targetKonstruction ?: return
                cancel()
                workManager.doWork {
                    val service = RootScope.serviceHolder.service.first()
                    service.get(targetWorkspace).use { ks ->
                        ks.delete(Konstruction("", targetWorkspace, targetKonstruction))
                    }
                    WorkspaceModel.refreshAllKonstructions()
                }
            }

            else -> {
                error("Unsupported dialog type")
            }
        }
    }
}
