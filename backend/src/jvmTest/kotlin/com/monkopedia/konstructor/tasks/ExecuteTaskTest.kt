package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.CompilationStatus.FAILURE
import com.monkopedia.konstructor.common.CompilationStatus.SUCCESS
import com.monkopedia.konstructor.common.TaskResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer

class ExecuteTaskTest {

    @Test
    fun `compiled script prints text`() {
        val result = execCode(
            "a",
            """
            package a
            fun main(args: Array<String>) = println("")
            """.trimIndent()
        )
        // No exit
        assertEquals(FAILURE, result.status)
    }

    @Test
    fun `compiled script exits with failure`() {
        val result = execCode(
            "a",
            """
            package a
            fun main(args: Array<String>) = 1.also { println("") }
            """.trimIndent()
        )
        assertEquals(FAILURE, result.status)
    }

    @Test
    fun `manually print call`() {
        val result = execCode(
            "a",
            """
            package a
            
            fun main(args: Array<String>) = 0.also {
                println("Content-Length: 2\nMethod: close\n\n{}\n")
                Thread.sleep(1000)
            }
            """.trimIndent()
        )
        Unit.serializer()
        assertEquals(SUCCESS, result.status)
    }

    @Test
    fun `call build`() {
        val result = execCode(
            "a",
            """
            package a
            import com.monkopedia.konstructor.lib.*
            
            fun main(args: Array<String>) = build(args) {
            }
            """.trimIndent()
        )
        Unit.serializer()
        assertEquals(SUCCESS, result.status)
    }

    fun execCode(pkg: String = "com.monkopedia.test", code: String): TaskResult {
        val testDirectory = kotlin.io.path.createTempDirectory().toFile()
        testDirectory.mkdirs()
        val testFile = File(testDirectory, "convert.kt")
        testFile.writeText(code)
        require(testFile.exists())
        val outputDirectory = kotlin.io.path.createTempDirectory().toFile()
        outputDirectory.mkdirs()
        val result = runBlocking {
            CompileTask(Config(), testFile, outputDirectory).execute()
        }
        assertEquals(SUCCESS, result.status)
        return runBlocking {
            ExecuteTask(pkg, "ConvertKt", outputDirectory, Config()).execute()
        }
    }
}
