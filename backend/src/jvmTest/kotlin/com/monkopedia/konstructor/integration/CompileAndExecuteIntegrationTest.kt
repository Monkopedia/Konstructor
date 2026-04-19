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
package com.monkopedia.konstructor.integration

import com.monkopedia.konstructor.KonstructionControllerImpl.Companion.copyContentToScript
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.tasks.CompileTask
import com.monkopedia.konstructor.testutil.TestEnvironment
import com.monkopedia.konstructor.testutil.TestFixtures.SIMPLE_CUBE_SCRIPT
import com.monkopedia.konstructor.testutil.TestFixtures.SYNTAX_ERROR_SCRIPT
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class CompileAndExecuteIntegrationTest {

    private var env: TestEnvironment? = null

    @Before
    fun setUp() {
        // These tests require the full build (lib shadowJar + backend resources).
        // Run via: ./gradlew shadowJar :backend:jvmTest -Dintegration=true
        assumeTrue(
            "Set -Dintegration=true to run compile integration tests",
            System.getProperty("integration") == "true"
        )
        env = TestEnvironment()
    }

    @After
    fun tearDown() {
        env?.close()
    }

    @Test
    fun testCompileSimpleCubeScript() = runBlocking {
        val pathController = PathController(env!!.config)
        val paths = pathController["0", "0"]
        copyContentToScript(SIMPLE_CUBE_SCRIPT.byteInputStream(), paths.kotlinFile)
        val result = CompileTask(env!!.config, paths.kotlinFile, paths.compileOutput).execute()
        assertEquals(SUCCESS, result.status, "Compile result: $result")
    }

    @Test
    fun testCompileInvalidScript() = runBlocking {
        val pathController = PathController(env!!.config)
        val paths = pathController["0", "0"]
        copyContentToScript(SYNTAX_ERROR_SCRIPT.byteInputStream(), paths.kotlinFile)
        val result = CompileTask(env!!.config, paths.kotlinFile, paths.compileOutput).execute()
        assertEquals(FAILURE, result.status)
        assertTrue(result.messages.isNotEmpty(), "Expected compile error messages")
    }

    @Test
    fun testCompileErrorHasLineInfo() = runBlocking {
        val pathController = PathController(env!!.config)
        val paths = pathController["0", "0"]
        copyContentToScript(SYNTAX_ERROR_SCRIPT.byteInputStream(), paths.kotlinFile)
        val result = CompileTask(env!!.config, paths.kotlinFile, paths.compileOutput).execute()
        assertEquals(FAILURE, result.status)
        assertTrue(
            result.messages.any { it.line != null },
            "Expected at least one message with line info, got: ${result.messages}"
        )
    }
}
