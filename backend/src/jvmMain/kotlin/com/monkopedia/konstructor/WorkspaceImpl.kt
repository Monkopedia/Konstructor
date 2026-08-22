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
@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.DirtyState.CLEAN
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import com.monkopedia.konstructor.logging.LoggingService
import com.monkopedia.konstructor.logging.callContext
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

class WorkspaceImpl(private val config: Config, private val workspaceId: String) :
    Workspace,
    LoggingService {
    override val serviceName: String
        get() = "Workspace"

    override suspend fun list(u: Unit): List<Konstruction> = callContext("list") {
        workspaceDir.listInfo<KonstructionInfo>(config.json).map { it.konstruction }
    }

    override suspend fun getName(u: Unit): String = callContext("getName") {
        infoFile.inputStream().use { input ->
            config.json.decodeFromStream<Space>(input).name
        }
    }

    override suspend fun setName(name: String) = callContext("setName") {
        val info = infoFile.inputStream().use { input ->
            config.json.decodeFromStream<Space>(input)
        }.copy(name = name)
        infoFile.outputStream().use { output ->
            config.json.encodeToStream(info, output)
        }
    }

    override suspend fun create(newItem: Konstruction): Konstruction = callContext("create") {
        if (newItem.workspaceId != workspaceId) {
            throw IllegalArgumentException("Trying to create item in wrong workspace")
        }
        // Atomic claim — see createWithClaimedId and konstructor#102.
        val claimedId = createWithClaimedId(workspaceDir, newItem.id, config.json) { id ->
            KonstructionInfo(newItem.copy(id = id), CLEAN, emptyList())
        }
        newItem.copy(id = claimedId)
    }

    override suspend fun delete(item: Konstruction): Unit = callContext("delete") {
        val targetInfo = item.infoFile
        if (!targetInfo.exists()) {
            throw IllegalArgumentException("Can't find workspace $item")
        }
        targetInfo.parentFile.deleteRecursively()
    }

    private val workspaceDir = File(config.dataDir, workspaceId)
    private val infoFile = File(workspaceDir, INFO_JSON)
    private val Konstruction.infoFile: File
        get() = File(File(workspaceDir, id), INFO_JSON)
}
