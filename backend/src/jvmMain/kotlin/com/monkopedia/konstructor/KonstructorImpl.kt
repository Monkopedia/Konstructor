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

import com.monkopedia.hauler.Flatbed
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.attach
import com.monkopedia.hauler.deliveries
import com.monkopedia.hauler.route
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.LogFormatter
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import com.monkopedia.konstructor.logging.LoggingService
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.logging.callContext
import com.monkopedia.konstructor.logging.writeBinary
import com.monkopedia.konstructor.logging.writeText
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@OptIn(DelicateCoroutinesApi::class)
class KonstructorImpl(private val config: Config) : Konstructor, LoggingService {
    private val mutex = Mutex()
    private val konstructionLookup = mutableMapOf<Pair<String, String>, KonstructionService>()
    private val warehouse = WarehouseWrapper()
    override val serviceName: String
        get() = "Konstructor"

    // As long as the process is alive the konstructor should live.
    private val scope = GlobalScope

    init {
        scope.launch {
            warehouse.requestPickup().attach(scope)
            warehouse.writeBinary(File(config.dataDir, "log.bin"))
            warehouse.writeText(File(config.dataDir, "log.txt"))
            warehouse.deliveries().deliveries().route(Flatbed, LogFormatter)
        }
    }

    override suspend fun list(u: Unit): List<Space> = callContext("list") {
        config.dataDir.listFiles()?.mapNotNull {
            if (!it.isDirectory) return@mapNotNull null
            val infoFile = File(it, "info.json")
            if (!infoFile.exists()) return@mapNotNull null
            infoFile.inputStream().use { input ->
                config.json.decodeFromStream<Space>(input)
            }
        } ?: emptyList()
    }

    override suspend fun konstruction(id: Konstruction): KonstructionService =
        callContext("konstruction") {
            mutex.withLock {
                konstructionLookup.getOrPut(id.workspaceId to id.id) {
                    KonstructionServiceImpl(config, id.workspaceId, id.id, warehouse) {
                        mutex.withLock {
                            konstructionLookup.remove(id.workspaceId to id.id)
                        }
                    }
                }
            }
        }

    override suspend fun get(id: String): Workspace = callContext("get") {
        WorkspaceImpl(config, id)
    }

    override suspend fun create(newItem: Space): Space = callContext("create") {
        val newItemWithId = newItem.copy(
            id = newItem.id.ifEmpty { generateId() }
        )
        val targetInfo = newItemWithId.infoFile
        if (targetInfo.exists() || targetInfo.parentFile.exists()) {
            throw IllegalArgumentException("${newItemWithId.id} has been used already")
        }
        targetInfo.parentFile.mkdirs()
        targetInfo.outputStream().use { output ->
            config.json.encodeToStream(newItemWithId, output)
        }
        newItemWithId
    }

    private suspend fun generateId(): String = callContext("generateId") {
        val usedIds = list().map { it.id }
        var id = 0
        while (usedIds.contains(id.toString())) {
            id++
        }
        id.toString()
    }

    override suspend fun delete(item: Space): Unit = callContext("delete") {
        val targetInfo = item.infoFile
        if (!targetInfo.exists()) {
            throw IllegalArgumentException("Can't find workspace $item")
        }
        targetInfo.parentFile.deleteRecursively()
    }

    override suspend fun getGlobalShipper(u: Unit): Shipper = callContext("getGlobalShipper") {
        warehouse
    }

    fun getInputStream(target: String): InputStream {
        return File(config.dataDir, target).inputStream()
    }

    private val Space.infoFile: File
        get() = File(File(config.dataDir, id), "info.json")
}
