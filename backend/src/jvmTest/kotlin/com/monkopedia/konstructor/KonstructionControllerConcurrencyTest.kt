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
@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.junit.After
import org.junit.Before

/**
 * Regression coverage for the content-file lock. The controller previously used a
 * homebrew JVM-monitor lock (`SimpleLock`) that blocked threads and could be left
 * permanently held if a coroutine acquired it and was then cancelled before its
 * `finally` released it — the deadlock seen on adolin 2026-05-19 (see konstructor#1).
 *
 * These tests fail (hang → timeout) if the lock is not coroutine-aware and
 * cancellation-safe.
 */
class KonstructionControllerConcurrencyTest {

    private lateinit var env: TestEnvironment
    private lateinit var controller: KonstructionControllerImpl

    @Before
    fun setUp() {
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test")
        createKonstructionInfo("ws1", "k1", "test-k")
        controller = KonstructionControllerImpl(env.config, "ws1", "k1", Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun createKonstructionInfo(workspaceId: String, id: String, name: String) {
        val dir = File(env.tempDir, "$workspaceId/$id")
        dir.mkdirs()
        val info = KonstructionInfo(
            Konstruction(name = name, workspaceId = workspaceId, id = id),
            DirtyState.CLEAN
        )
        File(dir, "info.json").outputStream().use {
            env.config.json.encodeToStream(info, it)
        }
    }

    @Test
    fun concurrentReadsAndWritesDoNotDeadlock() = runBlocking {
        // If the lock wedges, withTimeout aborts the test rather than hanging CI.
        withTimeout(10_000) {
            coroutineScope {
                (0 until 50).map { i ->
                    async(Dispatchers.IO) {
                        controller.write("content-$i")
                        controller.read()
                    }
                }.awaitAll()
            }
        }
        // Whatever the interleaving, the controller must still be usable and
        // return one of the values that was written.
        val finalRead = withTimeout(5_000) { controller.read() }
        assertTrue(finalRead.startsWith("content-"), "unexpected content: $finalRead")
    }

    @Test
    fun cancelledWritersDoNotWedgeTheLock() = runBlocking {
        // Launch a batch of writers and cancel half of them at arbitrary points.
        // With the legacy SimpleLock, a writer cancelled while holding the lock
        // would leave isLocked == true forever; a coroutine-aware Mutex releases
        // on cancellation. The post-batch write+read must therefore still succeed.
        coroutineScope {
            val jobs = (0 until 20).map { i ->
                launch(Dispatchers.IO) {
                    controller.write("cancel-test-$i")
                }
            }
            jobs.filterIndexed { index, _ -> index % 2 == 0 }.forEach { it.cancel() }
            jobs.forEach { it.join() }
        }

        withTimeout(5_000) {
            controller.write("survivor")
            assertEquals("survivor", controller.read())
        }
    }

    @Test
    fun lockIsReleasedAfterWriteFailure() = runBlocking {
        // A failed operation inside the lock must still release it. Force a failure
        // by deleting the content directory out from under a write, then confirm a
        // subsequent normal write+read works (i.e. the lock was not orphaned).
        runCatching {
            File(env.tempDir, "ws1/k1").deleteRecursively()
            controller.write("will-maybe-fail")
        }
        createKonstructionInfo("ws1", "k1", "test-k")
        File(env.tempDir, "ws1/k1").mkdirs()

        withTimeout(5_000) {
            controller.write("recovered")
            assertEquals("recovered", controller.read())
        }
    }
}
