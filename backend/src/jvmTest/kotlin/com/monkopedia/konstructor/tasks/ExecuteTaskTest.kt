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
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.hostservices.InvalidScriptException
import com.monkopedia.konstructor.hostservices.ScriptManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ExecuteTaskTest {

    @Test(expected = InvalidScriptException::class)
    fun `compiled script exits with failure`() {
        execCode(
            """
            kotlin.system.exitProcess(1)
            """.trimIndent()
        )
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
        assertEquals(SUCCESS, result.status, "Result: $result")
    }

    /**
     * Regression test for #81 and #104.
     *
     * A multi-target render where one export fails at EXECUTE time (compiles fine, throws while
     * building/rendering) while the subprocess stays alive and another export succeeds. The
     * failing target is declared FIRST and the succeeding target LAST, which is exactly the
     * last-target-wins scenario that used to mask the failure:
     *   (a) `isSuccessful` was ASSIGNED per target, so `good` building after `bad` overwrote the
     *       failure and the whole render reported SUCCESS (the failed target got marked CLEAN and
     *       never retried).
     *   (b) the fetched error trace was discarded, so `messages` was always empty.
     *   (c) the reported target list was the *attempted* one, so the caller marked the failed
     *       target CLEAN and never retried it (#104).
     *
     * With the fix the render must report FAILURE, surface the error trace, and report only
     * the target that actually built.
     */
    @Test
    fun `multi-target render fails when one target fails at execute time`() {
        val result = execCode(
            """
            val bad by primitive {
               throw RuntimeException("boom-81")
            }
            val good by primitive {
               roundedCube {
                   dimensions = xyz(1.0, 1.0, 1.0)
               }
            }
            export("bad")
            export("good")
            """.trimIndent()
        )
        assertEquals(
            FAILURE,
            result.status,
            "A failed target must fail the whole render, even if a later target succeeds. " +
                "Result: $result"
        )
        assertTrue(
            result.messages.isNotEmpty(),
            "The execute-phase error trace must be surfaced in messages. Result: $result"
        )
        assertEquals(
            listOf("good"),
            result.taskArguments,
            "Only targets that actually built may be reported (#104) — reporting the " +
                "attempted list marks the failed target CLEAN, so it is never retried. " +
                "Result: $result"
        )
    }

    fun execCode(code: String): TaskResult {
        val testDirectory = kotlin.io.path.createTempDirectory().toFile()
        val config = Config(testDirectory)
        val pathController = PathController(config)
        val paths = pathController["0", "0"]
        copyContentToScript(code.byteInputStream(), paths.kotlinFile)
        require(paths.kotlinFile.exists())
        val result = runBlocking {
            CompileTask(config, paths.kotlinFile, paths.compileOutput).execute()
        }
        assertEquals(SUCCESS, result.status, "Result: $result")
        return runBlocking {
            val script = ScriptManager(config).getScript(paths, "ConvertKt")
            ExecuteTask(config, script, extraTargets = emptyList()).execute()
        }.first
    }
}
