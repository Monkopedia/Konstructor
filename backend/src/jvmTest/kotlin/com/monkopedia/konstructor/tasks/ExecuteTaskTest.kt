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
package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.KonstructionControllerImpl.Companion.copyContentToScript
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

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
