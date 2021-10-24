package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.CompilationStatus
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.common.TaskResult
import io.ktor.util.cio.toByteReadChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

@ExperimentalSerializationApi
class KonstructionServiceImpl(
    private val config: Config,
    private val workspaceId: String,
    private val id: String
) : KonstructionService {
    private val workspaceDir = File(config.dataDir, workspaceId)
    private val infoFile = File(File(workspaceDir, id), "info.json")
    private val contentFile = File(File(workspaceDir, id), "content")

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

    override suspend fun fetch(u: Unit): ByteReadChannel {
        if (!contentFile.exists()) {
            return ByteReadChannel(ByteArray(0))
        }
        return contentFile.inputStream().toByteReadChannel()
    }

    override suspend fun set(content: ByteReadChannel) {
        contentFile.outputStream().use { output ->
            content.copyTo(output)
        }
    }

    override suspend fun compile(u: Unit): TaskResult {
        return TaskResult(
            CompilationStatus.FAILURE,
            listOf(TaskMessage("Expected failure", 5))
        )
    }

    override suspend fun rendered(u: Unit): String? {
        return "/model/suzanne.stl"
    }

    override suspend fun render(u: Unit): TaskResult {
        return TaskResult(
            CompilationStatus.FAILURE,
            listOf(TaskMessage("Expected failure", 5))
        )
    }
}
