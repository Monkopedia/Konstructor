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
import com.monkopedia.konstructor.common.KonstructionCallbacks.RENDER_CHANGE
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus.FAILURE
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.testutil.FakeKonstructionListener
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
 * Covered in both positions a target can be in: failing on its first render, and failing on a
 * later one after it had already built and been recorded CLEAN. The second is the one that
 * survives a fix which only asks "was it built?" and otherwise keeps the state it had.
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
        val listener = FakeKonstructionListener(callbacks = listOf(RENDER_CHANGE))
        service.register(listener)
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

        // The render deleted both STLs up front, so the failed target has no model any more
        // and the editor has to be told — otherwise it keeps drawing geometry whose file is
        // gone, and its own "have I got this render?" bookkeeping blocks a rebuild.
        val renders = awaitRenders(listener, "bad", "good")
        assertNotNull(
            renders["good"],
            "The built target must broadcast its model. Renders: $renders"
        )
        assertNull(
            renders["bad"],
            "The failed target must broadcast a null render to clear the stale model. " +
                "Renders: $renders"
        )
    }

    /**
     * Wait for a render callback to arrive for each of [names] — they are dispatched on the
     * service's own scope, so they land after `konstruct()` returns — and return the render
     * path each one carried, by target name.
     */
    private suspend fun awaitRenders(
        listener: FakeKonstructionListener,
        vararg names: String
    ): Map<String, String?> {
        withTimeoutOrNull(5_000) {
            while (!listener.renderChanges.map { it.name }.containsAll(names.toList())) {
                delay(10)
            }
        }
        val renders = listener.renderChanges.associate { it.name to it.renderPath }
        assertTrue(
            renders.keys.containsAll(names.toList()),
            "Expected a render callback for each of ${names.toList()}, got $renders — a " +
                "target whose model was deleted and never rebuilt is left uncleared."
        )
        return renders
    }

    /**
     * The case the two tests above cannot reach: a target that already BUILT (so it is
     * recorded CLEAN) and then fails on a later render.
     *
     * Deriving its state as "not built, so keep whatever it had" is only right for a target's
     * first render — after that the state it had is CLEAN, and keeping it puts the target back
     * in exactly the #104 hole. Failing at execute time must make it dirty again regardless of
     * what it was before.
     */
    @Test
    fun alreadyCleanTargetThatFailsBecomesDirtyAgain() = runBlocking {
        val (_, second) = renderUntilACleanTargetFails()

        assertEquals(FAILURE, second.status, "Second render must fail. Result: $second")
        val targets = service.getInfo(Unit).targets.associate { it.name to it.state }
        assertEquals(
            CLEAN,
            targets["bad"],
            "The target that built on the second render must be clean. Targets: $targets"
        )
        assertEquals(
            NEEDS_EXEC,
            targets["other"],
            "A target that failed at execute time must go dirty again even though it had " +
                "already built once and was recorded CLEAN. Targets: $targets"
        )
    }

    /**
     * The end-to-end half of the same case: once a previously-clean target has failed, a
     * re-request must re-enter `render()` rather than re-broadcast the stale failure.
     *
     * As above, nothing about the konstruction changes between the failing render and the
     * retry — no edit, no recompile — so SUCCESS is only reachable by really rendering again.
     */
    @Test
    fun reRequestRetriesATargetThatFailedAfterAlreadyBuilding() = runBlocking {
        val (otherMarker, second) = renderUntilACleanTargetFails()
        assertEquals(FAILURE, second.status, "Second render must fail. Result: $second")

        otherMarker.delete()

        val third = service.konstruct("other")
        assertEquals(
            SUCCESS,
            third.status,
            "A target that failed after already building must still be retriable — a FAILURE " +
                "here is the stale result being re-broadcast because render() was skipped. " +
                "Result: $third"
        )
        assertEquals(
            CLEAN,
            service.getInfo(Unit).targets.single { it.name == "other" }.state,
            "The retried target must end up clean"
        )
    }

    /**
     * Drives the two renders both of the tests above need: a first one that leaves `other`
     * CLEAN and `bad` dirty, then a second (reached through `bad`, with the failures swapped
     * out of band) in which the already-clean `other` fails at execute time.
     *
     * Returns `other`'s marker file — deleting it un-breaks the target — and the second
     * render's result.
     */
    private suspend fun renderUntilACleanTargetFails(): Pair<File, TaskResult> {
        val badMarker = File(env.tempDir, "break-bad")
        val otherMarker = File(env.tempDir, "break-other")
        service.set(
            """
            val bad by primitive {
               if (java.io.File("${badMarker.absolutePath}").exists()) {
                   throw RuntimeException("bad-boom")
               }
               cube {
                   dimensions = xyz(1.0, 1.0, 1.0)
               }
            }
            val other by primitive {
               if (java.io.File("${otherMarker.absolutePath}").exists()) {
                   throw RuntimeException("other-boom")
               }
               cube {
                   dimensions = xyz(2.0, 2.0, 2.0)
               }
            }
            export("bad")
            export("other")
            """.trimIndent()
        )
        assertEquals(SUCCESS, service.compile(Unit).status, "The script must compile")

        badMarker.writeText("break")
        val first = service.konstruct("bad")
        assertEquals(FAILURE, first.status, "First render must fail. Result: $first")
        assertEquals(
            CLEAN,
            service.getInfo(Unit).targets.single { it.name == "other" }.state,
            "Setup expects 'other' to have built and be clean before it starts failing"
        )

        // Swap which target is broken. No edit and no recompile, so the only thing that can
        // change 'other' from clean to failed is the render itself.
        badMarker.delete()
        otherMarker.writeText("break")

        return otherMarker to service.konstruct("bad")
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
