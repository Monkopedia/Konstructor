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
package com.monkopedia.konstructor.hostservices

import com.monkopedia.konstructor.KonstructorManager
import com.monkopedia.konstructor.WorkspaceImpl
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.tasks.ExecUtil.ExecProcess
import com.monkopedia.konstructor.testutil.TestEnvironment
import com.monkopedia.konstructor.testutil.recordFailures
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * konstructor#164: the per-konstruction script lock is taken before the subprocess starts, and
 * only the exit watcher releases it. A start that throws leaves no watcher, so the lock must be
 * released on that path, once, without the normal path releasing it twice.
 */
class ScriptManagerLockTest {

    private val env = TestEnvironment()

    @AfterTest
    fun tearDown() {
        env.close()
    }

    @Test
    fun aFailedStartDoesNotLeakTheLock() = runBlocking {
        val id = createKonstruction()
        var starts = 0
        val manager = ScriptManager(env.config) {
            starts++
            throw IOException("probe: Too many open files")
        }

        // Bounded: with the leak, the second attempt waits on the lock forever.
        repeat(2) {
            withTimeout(BOUND) {
                assertFailsWith<IOException> {
                    manager.getScriptWithExit(env.pathController[WS, id], "probe")
                }
            }
        }

        assertEquals(2, starts, "the second execute must get past the lock to start a process")
        assertFalse(scriptLock(id).isLocked, "a failed start must leave the lock free")
    }

    @Test
    fun aStartedProcessHoldsTheLockUntilItExitsAndReleasesItOnce() = runBlocking {
        val id = createKonstruction()
        val lock = scriptLock(id)
        val manager = ScriptManager(env.config) {
            ExecProcess(ProcessBuilder("sleep", "30").start())
        }

        val recorder = recordFailures {
            val caller = CoroutineScope(Dispatchers.IO).launch {
                // Cancellation surfaces from here wrapped as InvalidScriptException.
                runCatching { manager.getScriptWithExit(env.pathController[WS, id], "probe") }
            }
            withTimeout(BOUND) { while (!lock.isLocked) delay(10) }
            delay(SETTLE)
            assertTrue(lock.isLocked, "the lock must stay held while the process is alive")

            // Cancelling the caller kills the process; its exit is what releases the lock.
            caller.cancelAndJoin()
            withTimeout(BOUND) { lock.lock() }
            lock.unlock()
            delay(SETTLE)
        }

        // A second unlock would throw IllegalStateException in the watcher, which logs it.
        assertTrue(
            recorder.errorLogs.none { "Script lock watcher" in it },
            "the lock was released more than once: ${recorder.errorLogs}"
        )
        assertTrue(recorder.uncaught.isEmpty(), "uncaught: ${recorder.uncaught}")
    }

    private suspend fun createKonstruction(): String {
        env.createWorkspaceDir(WS, "Test Workspace")
        return WorkspaceImpl(env.config, WS)
            .create(Konstruction(name = "cube", workspaceId = WS, id = ""))
            .id
    }

    private fun scriptLock(id: String) =
        KonstructorManager(env.config).controllerFor(WS, id).scriptLock

    private companion object {
        const val WS = "ws1"
        val BOUND = 10.seconds
        val SETTLE = 300.milliseconds
    }
}
