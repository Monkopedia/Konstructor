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
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.testutil.FakeKonstructionListener
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream

/**
 * What `konstruct` reports when its dirty-state guard decides there is nothing to render.
 *
 * A konstruction is created CLEAN with no targets ([WorkspaceImpl.create]), so a konstruct
 * request that arrives before anything has been compiled skips the render — deterministically,
 * with no timing involved. `konstruct` used to read `result.json` regardless, which in that
 * state does not exist, so an ordinary call died with `FileNotFoundException` (#120).
 */
class KonstructionServiceKonstructTest {

    private lateinit var env: TestEnvironment
    private lateinit var scriptDir: File
    private lateinit var service: KonstructionServiceImpl

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test")
        scriptDir = File(env.tempDir, "ws1/k1").also { it.mkdirs() }
        // Exactly what WorkspaceImpl.create() writes: CLEAN, no targets, no render.
        val info = KonstructionInfo(
            Konstruction(name = "test", workspaceId = "ws1", id = "k1"),
            DirtyState.CLEAN
        )
        File(scriptDir, "info.json").outputStream().use {
            env.config.json.encodeToStream(info, it)
        }
        service = KonstructionServiceImpl(
            config = env.config,
            workspaceId = "ws1",
            id = "k1",
            warehouseWrapper = WarehouseWrapper(),
            onClose = {}
        )
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    private suspend fun awaitTaskComplete(listener: FakeKonstructionListener): TaskResult {
        withTimeout(5_000) {
            while (listener.taskCompletes.isEmpty()) {
                delay(10)
            }
        }
        return listener.taskCompletes.single()
    }

    @Test
    fun skippedRenderWithNoPriorRenderReportsFailureRatherThanThrowing() = runBlocking {
        assertTrue(
            !File(scriptDir, "result.json").exists(),
            "precondition: nothing has ever been rendered"
        )
        val listener = FakeKonstructionListener()
        service.register(listener)

        val result = service.konstruct("simpleCube")

        assertEquals(TaskStatus.FAILURE, result.status, "nothing was built")
        assertEquals(emptyList(), result.taskArguments, "no target was built")
        assertTrue(result.messages.isNotEmpty(), "the caller must be told why: $result")
        // The frontend leaves its spinner up until onTaskComplete lands (see
        // KonstructionViewModel.setTargetEnabled), so the broadcast still has to fire.
        assertEquals(result, awaitTaskComplete(listener), "onTaskComplete payload")
    }

    @Test
    fun skippedRenderReportsThePriorRenderResult() = runBlocking {
        val previous = TaskResult(listOf("simpleCube"), TaskStatus.SUCCESS)
        File(scriptDir, "result.json").outputStream().use {
            env.config.json.encodeToStream(previous, it)
        }
        val listener = FakeKonstructionListener()
        service.register(listener)

        // Already built and unchanged: konstruct still answers with that render, so a
        // caller re-requesting an up-to-date target keeps seeing SUCCESS.
        val result = service.konstruct("simpleCube")

        assertEquals(previous, result, "prior render result")
        assertEquals(previous, awaitTaskComplete(listener), "onTaskComplete payload")
    }
}
