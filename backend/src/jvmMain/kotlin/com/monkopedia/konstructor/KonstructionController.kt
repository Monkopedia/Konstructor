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

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.tasks.CompileTask
import com.monkopedia.konstructor.tasks.ExecuteTask
import java.io.File
import java.io.InputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

interface KonstructionController {
    var info: KonstructionInfo

    fun read(): String
    fun write(content: String)
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
    private val workspaceDir = File(config.dataDir, workspaceId)
    private val infoFile = File(File(workspaceDir, id), "info.json")
    private val compileResultFile = File(File(workspaceDir, id), "compile.json")
    private val compileOutput = File(File(workspaceDir, id), "out")
    private val renderResultFile = File(File(workspaceDir, id), "result.json")
    private val renderOutput = File(File(workspaceDir, id), "renders")
    private val contentFile = File(File(workspaceDir, id), "content.csgs")
    private val kotlinFile = File(File(workspaceDir, id), "content.kt")
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
            GlobalScope.launch(saveContext) {
                contentFileLock.withLock {
                    infoFile.outputStream().use { output ->
                        config.json.encodeToStream(value, output)
                    }
                }
                // Always set one more time after write to settle out any race conditions.
                infoImpl = value
            }
        }

    override suspend fun compile() {
        contentFileLock.withLock {
            withContext(Dispatchers.IO) {
                val inputStream = contentFile.inputStream()
                copyContentToScript(inputStream, kotlinFile)
            }
            val compileTask =
                CompileTask(config = config, input = kotlinFile, output = compileOutput)
            val result = compileTask.execute()
            compileResultFile.outputStream().use { output ->
                config.json.encodeToStream(result, output)
            }
        }
    }

    override suspend fun render(targets: List<String>): List<String> {
        contentFileLock.withLock {
            for (target in targets) {
                val targetFile = File(renderOutput, "$target.stl")
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
            renderOutput.mkdirs()
            val executeTask =
                ExecuteTask(
                    config = config,
                    outputDir = compileOutput,
                    renderOutputDir = renderOutput,
                    fileName = "ContentKt",
                    extraTargets = targets
                )
            val (result, executedTargets) = executeTask.execute()
            renderResultFile.outputStream().use { output ->
                config.json.encodeToStream(result, output)
            }
            return executedTargets
        }
    }

    override suspend fun lastRenderResult(): TaskResult = withContext(Dispatchers.IO) {
        renderResultFile.inputStream().use { input ->
            config.json.decodeFromStream(input)
        }
    }

    override suspend fun renderFile(target: String): File? {
        if (!renderOutput.exists() || !renderOutput.isDirectory) {
            return null
        }
        val target = File(renderOutput, "$target.stl")
        if (!target.exists()) {
            return null
        }
        return target
    }

    override suspend fun lastCompileResult(): TaskResult = withContext(Dispatchers.IO) {
        compileResultFile.inputStream().use { input ->
            config.json.decodeFromStream(input)
        }
    }

    private fun ensureLoaded() {
        synchronized(this) {
            if (hasInitialized) return
            infoImpl = infoFile.inputStream().use { output ->
                config.json.decodeFromStream(output)
            }
            hasInitialized = true
        }
    }

    override fun read(): String {
        if (!contentFile.exists()) {
            return ""
        }
        return contentFileLock.withLock {
            contentFile.readText()
        }
    }

    override fun write(content: String) {
        println("Write ${content.length} to $info")
        contentFileLock.withLock {
            contentFile.writeText(content)
        }
    }

    companion object {
        val KONSTRUCTION_FOOTER = """
            
            fun main(args: Array<String>) = com.monkopedia.konstructor.lib.runKonstruction(args, script)            
        """.trimIndent()

        fun copyContentToScript(inputStream: InputStream, kotlinFile: File) {
            kotlinFile.outputStream().use { os ->
                KcsgScript.HEADER.replace(
                    "com.monkopedia.kcsg.KcsgScript().apply",
                    "val script = com.monkopedia.kcsg.KcsgScript().apply"
                ).byteInputStream().copyTo(os)
                inputStream.use { it.copyTo(os) }
                KcsgScript.FOOTER.byteInputStream().copyTo(os)
                KONSTRUCTION_FOOTER.byteInputStream().copyTo(os)
            }
        }
    }
}
