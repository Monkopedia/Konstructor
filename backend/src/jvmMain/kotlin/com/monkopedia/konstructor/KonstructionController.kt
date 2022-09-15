@file:OptIn(DelicateCoroutinesApi::class)

package com.monkopedia.konstructor

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.tasks.CompileTask
import com.monkopedia.konstructor.tasks.ExecuteTask
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

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
                contentFileLock.isLocked = true
                try {
                    infoFile.outputStream().use { output ->
                        config.json.encodeToStream(value, output)
                    }
                } finally {
                    contentFileLock.isLocked = false
                }
                // Always set one more time after write to settle out any race conditions.
                infoImpl = value
            }
        }

    override suspend fun compile() {
        contentFileLock.isLocked = true
        try {
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
        } finally {
            contentFileLock.isLocked = false
        }
    }

    override suspend fun render(targets: List<String>): List<String> {
        contentFileLock.isLocked = true
        try {
            renderOutput.deleteRecursively()
            renderOutput.mkdirs()
            val executeTask =
                ExecuteTask(
                    config = config,
                    outputDir = compileOutput,
                    renderOutputDir = renderOutput,
                    fileName = "ContentKt",
                    extraTargets = targets
                )
            val (result, targets) = executeTask.execute()
            renderResultFile.outputStream().use { output ->
                config.json.encodeToStream(result, output)
            }
            return targets
        } finally {
            contentFileLock.isLocked = false
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
        contentFileLock.isLocked = true
        return contentFile.readText().also {
            contentFileLock.isLocked = false
        }
    }

    override fun write(content: String) {
        contentFileLock.isLocked = true
        contentFile.writeText(content)

        contentFileLock.isLocked = false
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
