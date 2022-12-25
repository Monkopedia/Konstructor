package com.monkopedia.konstructor.hostservices

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.KonstructorManager
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.lib.ScriptConfiguration
import com.monkopedia.konstructor.lib.ScriptService
import com.monkopedia.konstructor.tasks.ExecUtil
import com.monkopedia.ksrpc.toStub
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScriptManager private constructor(private val config: Config) {

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun getScript(paths: PathController.Paths): ScriptService {
        val opts = config.runtimeOpts
        val command = "kotlin $opts -cp ${paths.compileOutput.absolutePath} ContentKt"
        val scriptHost =
            ScriptHostImpl(this, config, paths.workspaceId, paths.konstructionId, paths.cacheDir)
        val lock = KonstructorManager(config).controllerFor(
            paths.workspaceId,
            paths.konstructionId
        ).scriptLock
        lock.lock()
        println("Opening script for ${paths.workspaceId}/${paths.konstructionId}")

        val exec = ExecUtil.executeWithChannel(command)
        val connection = exec.connection.await()
        val service = connection.defaultChannel().toStub<ScriptService>()
        GlobalScope.launch {
            println("Waiting for ${paths.workspaceId}/${paths.konstructionId}")
            exec.exitCode.await()
            println("Exit from ${paths.workspaceId}/${paths.konstructionId}")
            lock.unlock()
        }
        service.initializeHostServices(scriptHost)
        service.initialize(
            ScriptConfiguration(
                outputDirectory = paths.renderOutput.absolutePath,
                eagerExport = true
            )
        )
        println("Initialized ${paths.workspaceId}/${paths.konstructionId}")
        exec.parentScope.launch {
            delay(config.executeTimeout)
            println("Force killing ${paths.workspaceId}/${paths.konstructionId}")
            exec.kill()
        }
        return service
    }

    companion object : (Config) -> ScriptManager {
        private val managers = mutableMapOf<Config, ScriptManager>()

        override fun invoke(config: Config): ScriptManager {
            synchronized(managers) {
                return managers.getOrPut(config) {
                    ScriptManager(config)
                }
            }
        }
    }
}
