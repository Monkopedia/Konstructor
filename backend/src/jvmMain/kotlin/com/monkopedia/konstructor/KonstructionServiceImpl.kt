package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.DirtyState.CLEAN
import com.monkopedia.konstructor.common.DirtyState.NEEDS_COMPILE
import com.monkopedia.konstructor.common.DirtyState.NEEDS_EXEC
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.TaskResult

class KonstructionServiceImpl(private val config: Config, workspaceId: String, id: String) :
    KonstructionService {
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

    override suspend fun fetch(u: Unit): String {
        return konstructionController.read()
    }

    override suspend fun set(content: String) {
        konstructionController.write(content)
        konstructionController.info = konstructionController.info.copy(
            dirtyState = NEEDS_COMPILE
        )
    }

    override suspend fun compile(u: Unit): TaskResult {
        if (konstructionController.info.dirtyState == NEEDS_COMPILE) {
            konstructionController.compile()
            konstructionController.info = konstructionController.info.copy(
                dirtyState = NEEDS_EXEC
            )
        }
        return konstructionController.lastCompileResult()
    }

    override suspend fun rendered(u: Unit): String? {
        return konstructionController.firstRenderFile()?.toRelativeString(config.dataDir)
            ?.let { "model/$it" }
    }

    override suspend fun render(u: Unit): TaskResult {
        if (konstructionController.info.dirtyState == NEEDS_EXEC) {
            konstructionController.render()
            konstructionController.info = konstructionController.info.copy(
                dirtyState = CLEAN
            )
        }
        return konstructionController.lastRenderResult()
    }
}
