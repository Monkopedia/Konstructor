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

package com.monkopedia.konstructor.integration

import com.monkopedia.konstructor.KonstructionServiceImpl
import com.monkopedia.konstructor.common.DirtyState.CLEAN
import com.monkopedia.konstructor.common.DirtyState.NEEDS_EXEC
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for #104: a target that fails at execute time must stay dirty so a later request
 * retries it.
 *
 * #81 made a partial render report FAILURE, but the failed target was still recorded CLEAN
 * (dirty state came from the *attempted* target list, not from what actually built). With the
 * target CLEAN the guard in `KonstructionServiceImpl.konstruct()` skipped `render()` entirely
 * on the next request and just re-broadcast the stale failure — so re-running did nothing and
 * only an edit-and-recompile could clear it.
 *
 * These drive the real pipeline (kotlinc + the script subprocess) through
 * [KonstructionServiceImpl], because the defect only shows up in the interaction between what
 * `ExecuteTask` reports and how the service turns it into dirty state.
 */
class FailedRenderRetryTest {

    private lateinit var env: TestEnvironment
    private lateinit var service: KonstructionServiceImpl

    @Before
    fun setUp() {
        // Needs the full build (lib shadowJar bundled into backend resources), same as the
        // other compile+execute integration tests.
        assumeTrue(
            "Set -Dintegration=true to run compile integration tests",
            System.getProperty("integration") == "true"
        )
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test")
        val dir = File(env.tempDir, "ws1/k1")
        dir.mkdirs()
        val info = KonstructionInfo(
            Konstruction(name = "test", workspaceId = "ws1", id = "k1"),
            CLEAN
        )
        File(dir, "info.json").outputStream().use {
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

    @After
    fun tearDown() {
        if (::env.isInitialized) {
            env.close()
        }
    }

    /**
     * Both directions of the dirty state a render leaves behind: the target that built is
     * CLEAN, the target that failed is still NEEDS_EXEC.
     */
    @Test
    fun failedTargetStaysDirtyWhileBuiltTargetGoesClean() = runBlocking {
        service.set(
            """
            val bad by primitive {
               throw RuntimeException("boom-104")
            }
            val good by primitive {
               cube {
                   dimensions = xyz(1.0, 1.0, 1.0)
               }
            }
            export("bad")
            export("good")
            """.trimIndent()
        )
        assertEquals(SUCCESS, service.compile(Unit).status, "The script must compile")

        val result = service.konstruct("bad")
        assertEquals(FAILURE, result.status, "Render result: $result")

        val targets = service.getInfo(Unit).targets.associate { it.name to it.state }
        assertEquals(
            CLEAN,
            targets["good"],
            "A target that built must be marked clean. Targets: $targets"
        )
        assertEquals(
            NEEDS_EXEC,
            targets["bad"],
            "A target that failed at execute time must stay dirty so it can be retried. " +
                "Targets: $targets"
        )
    }

    /**
     * The end-to-end half: a re-request on a failed target must actually re-enter `render()`.
     *
     * The script fails until a marker file appears, and nothing else about the konstruction
     * changes between the two requests — no edit, no recompile. So the second request can only
     * report SUCCESS if it really ran the render again; if the guard skips it, the stale
     * FAILURE comes back instead. That silent skip is the failure mode, which is why this
     * asserts the retry rather than just the dirty flag.
     */
    @Test
    fun reRequestOfAFailedTargetRunsTheRenderAgain() = runBlocking {
        val marker = File(env.tempDir, "let-it-build")
        service.set(
            """
            val flaky by primitive {
               if (!java.io.File("${marker.absolutePath}").exists()) {
                   throw RuntimeException("boom-104")
               }
               cube {
                   dimensions = xyz(1.0, 1.0, 1.0)
               }
            }
            export("flaky")
            """.trimIndent()
        )
        assertEquals(SUCCESS, service.compile(Unit).status, "The script must compile")

        val first = service.konstruct("flaky")
        assertEquals(FAILURE, first.status, "First render must fail. Result: $first")
        assertNull(service.konstructed("flaky"), "A failed render must leave no model")

        marker.writeText("go")

        val second = service.konstruct("flaky")
        assertEquals(
            SUCCESS,
            second.status,
            "Re-requesting a failed target must re-run the render — a FAILURE here is the " +
                "stale result being re-broadcast because render() was skipped. Result: $second"
        )
        assertNotNull(service.konstructed("flaky"), "The retry must produce a model")
        assertEquals(
            CLEAN,
            service.getInfo(Unit).targets.single { it.name == "flaky" }.state,
            "The retried target must end up clean"
        )
    }
}
