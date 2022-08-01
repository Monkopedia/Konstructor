package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

class KonstructorImpl(private val config: Config) : Konstructor {

    @ExperimentalSerializationApi
    override suspend fun list(u: Unit): List<Space> {
        return config.dataDir.listFiles().mapNotNull {
            if (!it.isDirectory) return@mapNotNull null
            val infoFile = File(it, "info.json")
            if (!infoFile.exists()) return@mapNotNull null
            infoFile.inputStream().use { input ->
                config.json.decodeFromStream<Space>(input)
            }
        }
    }

    override suspend fun konstruction(id: Konstruction): KonstructionService {
        return KonstructionServiceImpl(config, id.workspaceId, id.id)
    }

    override suspend fun get(id: String): Workspace {
        return WorkspaceImpl(config, id)
    }

    @ExperimentalSerializationApi
    override suspend fun create(newItem: Space): Space {
        val newItem = newItem.copy(
            id = newItem.id.ifEmpty { generateId() }
        )
        val targetInfo = newItem.infoFile
        if (targetInfo.exists() || targetInfo.parentFile.exists()) {
            throw IllegalArgumentException("${newItem.id} has been used already")
        }
        targetInfo.parentFile.mkdirs()
        targetInfo.outputStream().use { output ->
            config.json.encodeToStream(newItem, output)
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

    override suspend fun delete(item: Space) {
        val targetInfo = item.infoFile
        if (!targetInfo.exists()) {
            throw IllegalArgumentException("Can't find workspace $item")
        }
        targetInfo.parentFile.deleteRecursively()
    }

    private val Space.infoFile: File
        get() = File(File(config.dataDir, id), "info.json")
}
