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
package com.monkopedia.konstructor.frontend.viewmodel

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionType
import com.monkopedia.konstructor.common.Space
import io.ktor.utils.io.ByteReadChannel

/**
 * Single home for workspace/konstruction create/rename/delete RPCs.
 *
 * Every mutation used to be hand-written 2–3 times (the production dialogs in
 * [NavigationDialogViewModel], [SpaceListViewModel], and the e2e [JsBridge]),
 * each repeating the `service.value ?: return` / `service.get(id)` / mutate
 * shape and the "find a konstruction by id in `ws.list()`" logic. This class
 * owns that shape once; callers keep their own refresh / error-reporting policy
 * (dialogs swallow, the bridge calls `setError`) around these calls.
 *
 * These functions perform the raw RPC and propagate failures — the caller
 * decides how to react. Returns are non-null on success; a `null` return means
 * the service was not connected (there was nothing to mutate).
 */
class WorkspaceMutations(private val serviceHolder: ServiceHolder) {

    suspend fun createWorkspace(name: String): Space? {
        val service = serviceHolder.service.value ?: return null
        return service.create(Space(id = "", name = name))
    }

    suspend fun deleteWorkspace(space: Space) {
        val service = serviceHolder.service.value ?: return
        service.delete(space)
    }

    suspend fun renameWorkspace(id: String, name: String) {
        val service = serviceHolder.service.value ?: return
        service.get(id).setName(name)
    }

    suspend fun createKonstruction(workspaceId: String, name: String): Konstruction? {
        val service = serviceHolder.service.value ?: return null
        return service.get(workspaceId)
            .create(Konstruction(name = name, workspaceId = workspaceId, id = ""))
    }

    suspend fun deleteKonstruction(konstruction: Konstruction) {
        val service = serviceHolder.service.value ?: return
        service.get(konstruction.workspaceId).delete(konstruction)
    }

    suspend fun renameKonstruction(workspaceId: String, id: String, name: String) {
        val service = serviceHolder.service.value ?: return
        service.konstruction(Konstruction(name = "", workspaceId = workspaceId, id = id))
            .setName(name)
    }

    suspend fun uploadStl(workspaceId: String, name: String, data: ByteArray) {
        val service = serviceHolder.service.value ?: return
        val konstruction = service.get(workspaceId).create(
            Konstruction(
                name = name,
                workspaceId = workspaceId,
                id = "",
                type = KonstructionType.STL
            )
        )
        service.konstruction(konstruction).setBinary(ByteReadChannel(data))
    }

    /**
     * Look up a konstruction by id within [workspaceId]. The e2e bridge
     * addresses konstructions by id, so this resolves the [Konstruction] the
     * konstruction-service RPCs require. Returns `null` when disconnected or no
     * konstruction matches.
     */
    suspend fun findKonstruction(workspaceId: String, id: String): Konstruction? {
        val service = serviceHolder.service.value ?: return null
        return service.get(workspaceId).list().firstOrNull { it.id == id }
    }
}
