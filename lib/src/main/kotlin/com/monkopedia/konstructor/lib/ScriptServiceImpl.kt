package com.monkopedia.konstructor.lib

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.lib.TargetStatus.NONE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.system.exitProcess

class ScriptServiceImpl(private val script: KcsgScript) : ScriptService {
    private val serviceCache = mutableMapOf<String, BuildServiceImpl>()
    private val statusCache = mutableMapOf<String, TargetStatus>()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val lock = Mutex()
    private lateinit var outputDirectory: File
    private var isInitialized = false

    override suspend fun initialize(config: ScriptConfiguration) {
        if (isInitialized) {
            throw IllegalArgumentException("Already initialized")
        }
        outputDirectory = File(config.outputDirectory)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IllegalArgumentException("Can't write to ${outputDirectory.absolutePath}")
        }
        isInitialized = true
        if (config.eagerExport) {
            for (target in listTargets(onlyExports = true)) {
                buildTarget(target.name)
            }
        }
    }

    override suspend fun listTargets(onlyExports: Boolean): List<ScriptTargetInfo> {
        val targets = if (onlyExports) script.exports() else script.targets()
        lock.withLock {
            return targets.map { target ->
                ScriptTargetInfo(
                    target,
                    serviceCache[target]?.getStatus() ?: statusCache[target] ?: NONE
                )
            }
        }
    }

    override suspend fun buildTarget(name: String): BuildService {
        lock.withLock {
            return serviceCache.getOrPut(name) {
                BuildServiceImpl(scope, script, name, outputDirectory) { status ->
                    lock.withLock {
                        statusCache[name] = status
                        serviceCache.remove(name)
                    }
                }
            }
        }
    }

    override suspend fun closeService(u: Unit) {
        try {
            job.cancel()
        } finally {
            exitProcess(0)
        }
    }
}

