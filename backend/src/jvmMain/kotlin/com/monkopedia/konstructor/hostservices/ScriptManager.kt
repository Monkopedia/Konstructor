package com.monkopedia.konstructor.hostservices

import com.monkopedia.hauler.CallSign
import com.monkopedia.hauler.debug
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.info
import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.KonstructorManager
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.lib.ScriptConfiguration
import com.monkopedia.konstructor.lib.ScriptService
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.tasks.ExecUtil
import com.monkopedia.ksrpc.toStub
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

class ScriptManager private constructor(private val config: Config) {
    private val hauler by lazy { hauler() }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun getScript(paths: PathController.Paths, name: String): ScriptService {
        val opts = config.runtimeOpts
        val command = "kotlin $opts -cp ${paths.compileOutput.absolutePath} ContentKt"
        val scriptHost =
            ScriptHostImpl(this, config, paths.workspaceId, paths.konstructionId, paths.cacheDir)
        val lock = KonstructorManager(config).controllerFor(
            paths.workspaceId,
            paths.konstructionId
        ).scriptLock
        lock.lock()
        hauler.debug("Opening script for ${paths.workspaceId}/${paths.konstructionId}")

        val exec = ExecUtil.executeWithChannel(command)
        val connection = exec.connection.await()
        val service = connection.defaultChannel().toStub<ScriptService>()
        val callSign = coroutineContext[CallSign.Key]
        GlobalScope.launch(callSign ?: EmptyCoroutineContext) {
            hauler.info("Waiting for ${paths.workspaceId}/${paths.konstructionId}")
            exec.exitCode.await()
            hauler.info("Exit from ${paths.workspaceId}/${paths.konstructionId}")
            lock.unlock()
        }
        service.setShipper(
            shipper = WarehouseWrapper().getScoped(
                "${paths.workspaceId}.${paths.konstructionId}",
                name
            )
        )
        service.initializeHostServices(scriptHost)
        service.initialize(
            ScriptConfiguration(
                outputDirectory = paths.renderOutput.absolutePath,
                eagerExport = true
            )
        )
        hauler.debug("Initialized ${paths.workspaceId}/${paths.konstructionId}")
        exec.parentScope.launch(callSign ?: EmptyCoroutineContext) {
            delay(config.executeTimeout)
            hauler.debug("Force killing ${paths.workspaceId}/${paths.konstructionId}")
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
