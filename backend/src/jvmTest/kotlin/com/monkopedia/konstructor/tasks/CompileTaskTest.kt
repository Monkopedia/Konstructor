package com.monkopedia.konstructor.tasks

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.common.TaskMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class CompileTaskTest {
    @Test
    fun `parse sample kotlinc output`() {
        val offset = KcsgScript.HEADER.split("\n").size
        val testOutput = """
            |build.gradle.kts:${1 + offset}:1: error: unresolved reference: buildscript
            |buildscript {
            |^
            |build.gradle.kts:${2 + offset}:27: error: unresolved reference: extra
            |    val kotlin_version by extra("1.5.31")
            |                          ^
            |build.gradle.kts:${3 + offset}:5: error: unresolved reference: repositories
            |    repositories {
            |    ^
            |build.gradle.kts:${4 + offset}:9: error: unresolved reference: mavenCentral
            |        mavenCentral()
            |        ^
            |build.gradle.kts:${6 + offset}:5: error: unresolved reference: dependencies
            |    dependencies {
            |    ^
            |build.gradle.kts:${7 + offset}:9: error: unresolved reference: classpath
            |        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}kotlin_version")
            |        ^
            |build.gradle.kts:${8 + offset}:9: error: unresolved reference: classpath
            |        classpath("org.jetbrains.kotlin:kotlin-serialization:${'$'}kotlin_version")
            |        ^
            |build.gradle.kts:${9 + offset}:9: error: unresolved reference: classpath
            |        classpath("com.monkopedia:ksrpc-gradle-plugin:0.5.5")
            |        ^
            |""".trimMargin()
        assertEquals(
            listOf(
                TaskMessage("error: unresolved reference: buildscript", 1, 1),
                TaskMessage("error: unresolved reference: extra", 2, 27),
                TaskMessage("error: unresolved reference: repositories", 3, 5),
                TaskMessage("error: unresolved reference: mavenCentral", 4, 9),
                TaskMessage("error: unresolved reference: dependencies", 6, 5),
                TaskMessage("error: unresolved reference: classpath", 7, 9),
                TaskMessage("error: unresolved reference: classpath", 8, 9),
                TaskMessage("error: unresolved reference: classpath", 9, 9),

            ),
            CompileTask.parseErrors(testOutput.byteInputStream().bufferedReader())
        )
    }

    @Test
    fun `test general non-line error`() {
        val testOutput = """
            error: source entry is not a Kotlin file: gradle.properties
        """.trimIndent()
        assertEquals(
            listOf(TaskMessage("error: source entry is not a Kotlin file: gradle.properties")),
            CompileTask.parseErrors(testOutput.byteInputStream().bufferedReader())
        )
    }

    @Test
    fun `test execute compile a class`() {
        val testClass = """
            class TestClass {
                fun simpleMethod(): String = "A test string"
            }
        """.trimIndent()
        val testFile = kotlin.io.path.createTempFile(suffix = ".kt").toFile()
        testFile.writeText(testClass)
        require(testFile.exists())
        val outputDirectory = kotlin.io.path.createTempDirectory().toFile()
        outputDirectory.mkdirs()
        val result = runBlocking {
            CompileTask(Config(), testFile, outputDirectory).execute()
        }
        assertEquals(listOf(), result.messages)
        assertEquals(SUCCESS, result.status)
        testFile.deleteOnExit()
    }

    @Test
    fun `test importing library`() {
        val testClass = """

        """.trimIndent()
        val testFile = kotlin.io.path.createTempFile(suffix = ".kt").toFile()
        testFile.writeText(testClass)
        require(testFile.exists())
        val outputDirectory = kotlin.io.path.createTempDirectory().toFile()
        outputDirectory.mkdirs()
        val result = runBlocking {
            CompileTask(Config(), testFile, outputDirectory).execute()
        }
        assertEquals(listOf(), result.messages)
        assertEquals(SUCCESS, result.status)
        testFile.deleteOnExit()
    }
}
