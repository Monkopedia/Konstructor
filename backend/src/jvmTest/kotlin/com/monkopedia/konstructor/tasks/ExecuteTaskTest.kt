package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.KonstructionControllerImpl.Companion.copyContentToScript
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.common.TaskResult
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ExecuteTaskTest {

    @Test
    fun `compiled script exits with failure`() {
        val result = execCode(
            """
            kotlin.system.exitProcess(1)
            """.trimIndent()
        )
        assertEquals(FAILURE, result.status)
    }

    @Test
    fun `create something`() {
        val result = execCode(
            """
            val cyl by primitive {
               roundedCube {
                   dimensions = xyz(1.0, 2.0, 3.0)
                   cornerRadius = 0.5
               }
            }
            """.trimIndent()
        )
        assertEquals(SUCCESS, result.status)
    }

    fun execCode(code: String): TaskResult {
        val testDirectory = kotlin.io.path.createTempDirectory().toFile()
        testDirectory.mkdirs()
        val testFile = File(testDirectory, "convert.kt")
        copyContentToScript(code.byteInputStream(), testFile)
        require(testFile.exists())
        val outputDirectory = kotlin.io.path.createTempDirectory().toFile()
        outputDirectory.mkdirs()
        val result = runBlocking {
            CompileTask(Config(), testFile, outputDirectory).execute()
        }
        assertEquals(SUCCESS, result.status)
        return runBlocking {
            ExecuteTask("ConvertKt", outputDirectory, config = Config()).execute()
        }
    }
}
