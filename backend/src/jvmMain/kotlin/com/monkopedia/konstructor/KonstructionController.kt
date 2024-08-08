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
@file:OptIn(DelicateCoroutinesApi::class)

package com.monkopedia.konstructor

import com.monkopedia.hauler.CallSign
import com.monkopedia.hauler.debug
import com.monkopedia.hauler.hauler
import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.hostservices.ScriptManager
import com.monkopedia.konstructor.tasks.CompileTask
import com.monkopedia.konstructor.tasks.ExecuteTask
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import java.io.InputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

interface KonstructionController {
    val callSign: CallSign
    val paths: PathController.Paths
    val scriptLock: Mutex
    var info: KonstructionInfo

    fun read(): String
    suspend fun write(content: String)
    suspend fun write(content: ByteReadChannel)
    suspend fun compile()
    suspend fun lastCompileResult(): TaskResult
    suspend fun render(targets: List<String>): List<String>
    suspend fun lastRenderResult(): TaskResult
    suspend fun renderFile(target: String): File?
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class SimpleLock : Object() {
    var isLocked: Boolean = false
        set(value) {
            synchronized(this) {
                if (value) {
                    while (field) {
                        wait()
                    }
                    field = true
                } else {
                    require(field) {
                        "Lock cann't be unlocked when not locked"
                    }
                    field = false
                    notify()
                }
            }
        }
}

inline fun <R> SimpleLock.withLock(exec: () -> R): R {
    isLocked = true
    try {
        return exec()
    } finally {
        isLocked = false
    }
}

@OptIn(ExperimentalSerializationApi::class)
class KonstructionControllerImpl(
    private val config: Config,
    private val workspaceId: String,
    private val id: String,
    private val saveContext: CoroutineContext = newSingleThreadContext("KonstructionController")
) : KonstructionController {
    private val hauler = hauler()
    override val callSign: CallSign by lazy {
        CallSign("$workspaceId.$id.${info.konstruction.name}")
    }
    private val pathController = PathController(config)
    override val paths = pathController[workspaceId, id]
    private val contentFileLock = SimpleLock()
    private var hasInitialized = false
    private var infoImpl: KonstructionInfo? = null
    override var info: KonstructionInfo
        get() {
            ensureLoaded()
            return infoImpl!!
        }
        set(value) {
            if (infoImpl == value) return
            // Optimistically set now, to have the info available immediately.
            infoImpl = value
            GlobalScope.launch(saveContext + callSign) {
                contentFileLock.withLock {
                    paths.infoFile.outputStream().use { output ->
                        config.json.encodeToStream(value, output)
                    }
                }
                // Always set one more time after write to settle out any race conditions.
                infoImpl = value
            }
        }
    override val scriptLock: Mutex = Mutex()

    override suspend fun compile() {
        contentFileLock.withLock {
            withContext(Dispatchers.IO) {
                val inputStream = paths.contentFile.inputStream()
                copyContentToScript(inputStream, paths.kotlinFile)
            }
            val compileTask =
                CompileTask(config = config, input = paths.kotlinFile, output = paths.compileOutput)
            val result = compileTask.execute()
            paths.compileResultFile.outputStream().use { output ->
                config.json.encodeToStream(result, output)
            }
        }
    }

    override suspend fun render(targets: List<String>): List<String> {
        contentFileLock.withLock {
            for (target in targets) {
                val targetFile = File(paths.renderOutput, "$target.stl")
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
            paths.renderOutput.mkdirs()
            val script = ScriptManager(config).getScript(paths, info.konstruction.name)
            val executeTask = ExecuteTask(
                config = config,
                script = script,
                extraTargets = targets
            )
            val (result, executedTargets) = executeTask.execute()
            paths.renderResultFile.outputStream().use { output ->
                config.json.encodeToStream(result, output)
            }
            return executedTargets
        }
    }

    override suspend fun lastRenderResult(): TaskResult = withContext(Dispatchers.IO) {
        paths.renderResultFile.inputStream().use { input ->
            config.json.decodeFromStream(input)
        }
    }

    override suspend fun renderFile(target: String): File? {
        if (!paths.renderOutput.exists() || !paths.renderOutput.isDirectory) {
            return null
        }
        val target = File(paths.renderOutput, "$target.stl")
        if (!target.exists()) {
            return null
        }
        return target
    }

    override suspend fun lastCompileResult(): TaskResult = withContext(Dispatchers.IO) {
        paths.compileResultFile.inputStream().use { input ->
            config.json.decodeFromStream(input)
        }
    }

    private fun ensureLoaded() {
        synchronized(this) {
            if (hasInitialized) return
            infoImpl = paths.infoFile.inputStream().use { output ->
                config.json.decodeFromStream(output)
            }
            hasInitialized = true
        }
    }

    override fun read(): String {
        if (!paths.contentFile.exists()) {
            return ""
        }
        return contentFileLock.withLock {
            paths.contentFile.readText()
        }
    }

    override suspend fun write(content: String) {
        hauler.debug("Write ${content.length} to $info")
        contentFileLock.withLock {
            paths.contentFile.writeText(content)
        }
    }

    override suspend fun write(content: ByteReadChannel) {
        contentFileLock.withLock {
            paths.contentFile.outputStream().use {
                content.copyTo(it)
            }
        }
    }

    companion object {

        fun copyContentToScript(inputStream: InputStream, kotlinFile: File) {
            kotlinFile.outputStream().use { os ->
                (KcsgScript.HEADER.replace(
                    "com.monkopedia.kcsg.KcsgScript().apply",
                    "fun main(args: Array<String>) = com.monkopedia.konstructor.lib.runKonstruction(args, com.monkopedia.kcsg.KcsgScript())"
                ) + "\n").byteInputStream().copyTo(os)
                inputStream.use { it.copyTo(os) }
                ("\n" + KcsgScript.FOOTER).byteInputStream().copyTo(os)
            }
        }
    }
}
