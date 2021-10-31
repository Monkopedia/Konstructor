package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.DirtyState.NEEDS_COMPILE
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.tasks.CompileTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

interface KonstructionController {
    var info: KonstructionInfo

    fun inputStream(): InputStream
    fun outputStream(): OutputStream
    suspend fun compile()
    suspend fun lastCompileResult(): TaskResult
}

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

@ExperimentalSerializationApi
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
    private val contentFile = File(File(workspaceDir, id), "content.kt")
    private val contentFileLock = SimpleLock()
    private var hasInitialized = false
    private lateinit var infoImpl: KonstructionInfo
    override var info: KonstructionInfo
        get() {
            ensureLoaded()
            return info
        }
        set(value) {
            if (infoImpl == value) return
            // Optimistically set now, to have the info available immediately.
            infoImpl = value
            GlobalScope.launch(saveContext) {
                infoFile.outputStream().use { output ->
                    config.json.encodeToStream(value, output)
                }
                // Always set one more time after write to settle out any race conditions.
                infoImpl = value
            }
        }

    override suspend fun compile() {
        contentFileLock.isLocked = true
        try {
            val compileTask = CompileTask(config = config, input = contentFile, output = compileOutput)
            val result = compileTask.execute()
            compileResultFile.outputStream().use { output ->
                config.json.encodeToStream(result, output)
            }
        } finally {
            contentFileLock.isLocked = false
        }
    }

    override suspend fun lastCompileResult(): TaskResult = withContext(Dispatchers.IO) {
        compileResultFile.inputStream().use { input ->
            config.json.decodeFromStream(input)
        }
    }

    private fun ensureLoaded() {
        synchronized(this) {
            if (hasInitialized) return
            hasInitialized = true
        }
    }

    override fun inputStream(): InputStream {
        if (!contentFile.exists()) {
            return ByteArrayInputStream(byteArrayOf())
        }
        contentFileLock.isLocked = true
        return object : FileInputStream(contentFile) {
            override fun close() {
                contentFileLock.isLocked = false
                super.close()
            }
        }
    }

    override fun outputStream(): OutputStream {
        contentFileLock.isLocked = true
        return object : FileOutputStream(contentFile) {
            override fun close() {
                info = info.copy(
                    dirtyState = NEEDS_COMPILE
                )
                contentFileLock.isLocked = false
                super.close()
            }
        }
    }
}
