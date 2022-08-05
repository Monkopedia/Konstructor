package com.monkopedia.konstructor.lib

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.lib.TargetStatus.BUILDING
import com.monkopedia.konstructor.lib.TargetStatus.BUILT
import com.monkopedia.konstructor.lib.TargetStatus.ERROR
import com.monkopedia.konstructor.lib.TargetStatus.NONE
import com.monkopedia.ksrpc.asString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class BuildServiceImpl(
    private val scope: CoroutineScope,
    private val script: KcsgScript,
    private val name: String,
    outputDirectory: File,
    private val onClose: suspend (TargetStatus) -> Unit
) : BuildService {
    private val lock = Mutex()
    private val listeners = mutableListOf<BuildListener>()
    private var status: TargetStatus = NONE
    private var buildTime: Long = -1
    private val outputFile = File(outputDirectory, "$name.stl")
    private var errorMessage: String? = null

    init {
        scope.launch {
            val start = System.currentTimeMillis()
            status = BUILDING
            fireStatusUpdated()
            try {
                val result = script.generateTarget(name)
                val stl = result.toStlString()
                outputFile.writeText(stl)
                status = BUILT
            } catch (t: Throwable) {
                errorMessage = t.asString
                status = ERROR
            } finally {
                buildTime = System.currentTimeMillis() - start
            }
            fireStatusUpdated()
        }
    }

    private fun CoroutineScope.fireStatusUpdated() {
        launch {
            val (listeners, info) = lock.withLock {
                listeners.toList() to getInfo()
            }
            listeners.forEach { it.onStatusUpdated(info) }
        }
    }

    override suspend fun getInfo(u: Unit): ScriptTargetInfo {
        return ScriptTargetInfo(name, status)
    }

    override suspend fun registerListener(listener: BuildListener) {
        lock.withLock {
            listeners.add(listener)
        }
        scope.launch {
            if (status != NONE) {
                val info = getInfo()
                listener.onStatusUpdated(info)
            }
        }
    }

    override suspend fun getBuiltTarget(u: Unit): String {
        if (status == BUILT) {
            return outputFile.absolutePath
        } else {
            throw IllegalStateException("Build is not complete")
        }
    }

    override suspend fun getErrorTrace(u: Unit): String {
        if (status == ERROR) {
            return errorMessage!!
        } else {
            throw IllegalStateException("No error has occurred")
        }
    }

    override suspend fun getBuildLength(u: Unit): Long {
        return buildTime
    }

    fun getStatus(): TargetStatus {
        return status
    }

    override suspend fun close() {
        scope.launch {
            onClose(status)
        }
        super.close()
    }
}