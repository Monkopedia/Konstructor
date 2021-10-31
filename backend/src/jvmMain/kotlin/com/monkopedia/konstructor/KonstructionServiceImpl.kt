package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.CompilationStatus
import com.monkopedia.konstructor.common.DirtyState.NEEDS_COMPILE
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.TaskMessage
import com.monkopedia.konstructor.common.TaskResult
import io.ktor.util.cio.toByteReadChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo

class KonstructionServiceImpl(config: Config, workspaceId: String, id: String) : KonstructionService {
    private val konstructionController = KonstructorManager(config).controllerFor(workspaceId, id)

    override suspend fun getName(u: Unit): String {
        return konstructionController.info.konstruction.name
    }

    override suspend fun setName(name: String) {
        val info = konstructionController.info
        konstructionController.info = info.copy(
            konstruction = info.konstruction.copy(name = name)
        )
    }

    override suspend fun getInfo(u: Unit): KonstructionInfo {
        return konstructionController.info
    }

    override suspend fun fetch(u: Unit): ByteReadChannel {
        return konstructionController.inputStream().toByteReadChannel()
    }

    override suspend fun set(content: ByteReadChannel) {
        konstructionController.outputStream().use { output ->
            content.copyTo(output)
        }
    }

    override suspend fun compile(u: Unit): TaskResult {
        if (konstructionController.info.dirtyState == NEEDS_COMPILE) {
            konstructionController.compile()
        }
        return konstructionController.lastCompileResult()
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
