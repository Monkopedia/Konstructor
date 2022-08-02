package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.DirtyState.CLEAN
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.KonstructionType.OBJ
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

@ExperimentalSerializationApi
class WorkspaceImpl(private val config: Config, private val workspaceId: String) : Workspace {

    @ExperimentalSerializationApi
    override suspend fun list(u: Unit): List<Konstruction> {
        return workspaceDir.listFiles().mapNotNull {
            if (!it.isDirectory) return@mapNotNull null
            val infoFile = File(it, "info.json")
            if (!infoFile.exists()) return@mapNotNull null
            infoFile.inputStream().use { input ->
                config.json.decodeFromStream<KonstructionInfo>(input).konstruction
            }
        }
    }
    override suspend fun getName(u: Unit): String {
        return infoFile.inputStream().use { input ->
            config.json.decodeFromStream<Space>(input).name
        }
    }

    override suspend fun setName(name: String) {
        val info = infoFile.inputStream().use { input ->
            config.json.decodeFromStream<Space>(input)
        }.copy(name = name)
        infoFile.outputStream().use { output ->
            config.json.encodeToStream(info, output)
        }
    }

    @ExperimentalSerializationApi
    override suspend fun create(newItem: Konstruction): Konstruction {
        if (newItem.workspaceId != workspaceId) {
            throw IllegalArgumentException("Trying to create item in wrong workspace")
        }
        val newItem = newItem.copy(
            id = newItem.id.ifEmpty { generateId() }
        )
        val targetInfo = newItem.infoFile
        if (targetInfo.exists() || targetInfo.parentFile.exists()) {
            throw IllegalArgumentException("${newItem.id} has been used already")
        }
        targetInfo.parentFile.mkdirs()
        targetInfo.outputStream().use { output ->
            config.json.encodeToStream(KonstructionInfo(newItem, OBJ, CLEAN), output)
        }
        return newItem
    }

    private suspend fun generateId(): String {
        val usedIds = list(Unit).map { it.id }
        var id = 0
        while (usedIds.contains(id.toString())) {
            id++
        }
        return id.toString()
    }

    override suspend fun delete(item: Konstruction) {
        val targetInfo = item.infoFile
        if (!targetInfo.exists()) {
            throw IllegalArgumentException("Can't find workspace $item")
        }
        targetInfo.parentFile.deleteRecursively()
    }

    private val workspaceDir = File(config.dataDir, workspaceId)
    private val infoFile = File(workspaceDir, "info.json")
    private val Konstruction.infoFile: File
        get() = File(File(workspaceDir, id), "info.json")
}
