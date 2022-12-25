package com.monkopedia.konstructor.hostservices

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.KonstructorManager
import com.monkopedia.konstructor.WorkspaceImpl
import com.monkopedia.konstructor.common.DirtyState.CLEAN
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionType.CSGS
import com.monkopedia.konstructor.common.KonstructionType.STL
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.lib.HostService
import com.monkopedia.konstructor.lib.ScriptService
import java.io.File

class ScriptHostImpl(
    private val scriptManager: ScriptManager,
    private val config: Config,
    private val workspaceId: String,
    private val konstructionId: String,
    private val cacheDir: File
) : HostService {

    init {
        cacheDir.mkdirs()
    }

    override suspend fun supportsCaching(u: Unit): Boolean {
        return config.cachingEnabled
    }

    override suspend fun checkCached(hash: String): String? {
        val target = File(cacheDir, "$hash.stl")
        if (target.exists()) {
            return target.absolutePath
        }
        return null
    }

    override suspend fun findStl(stlName: String): String? {
        val target = findTarget(stlName)?.takeIf { it.type == STL } ?: return null
        val controller = KonstructorManager(config).controllerFor(target)
        return controller.paths.contentFile.absolutePath
    }

    override suspend fun storeCached(hash: String): String {
        val target = File(cacheDir, "$hash.stl")
        return target.absolutePath
    }

    override suspend fun findScript(csgsName: String): ScriptService {
        val target = findTarget(csgsName)?.takeIf { it.type == CSGS }
            ?: error("$csgsName does not reference a target")
        val controller = KonstructorManager(config).controllerFor(target)
        if (controller.info.dirtyState != CLEAN) {
            controller.compile()
            if (controller.lastCompileResult().status != SUCCESS) {
                error("$csgsName does not compile successfully")
            }
        }

        return scriptManager.getScript(controller.paths)
    }

    private suspend fun findTarget(targetName: String): Konstruction? {
        val workspace = WorkspaceImpl(config, workspaceId)
        val target = workspace.list().find {
            it.id == targetName || it.name == targetName
        } ?: return null
        if (target.id == konstructionId) {
            println("Warning: Script cannot target itself")
            return null
        }
        return target
    }
}
