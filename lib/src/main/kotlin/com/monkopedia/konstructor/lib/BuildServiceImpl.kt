/*
 * Copyright 2022 Jason Monk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.konstructor.lib

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.lib.TargetStatus.BUILDING
import com.monkopedia.konstructor.lib.TargetStatus.BUILT
import com.monkopedia.konstructor.lib.TargetStatus.ERROR
import com.monkopedia.konstructor.lib.TargetStatus.NONE
import com.monkopedia.ksrpc.asString
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BuildServiceImpl(
    private val scope: CoroutineScope,
    private val scriptDispatcher: CoroutineDispatcher,
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
            withContext(scriptDispatcher) {
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
