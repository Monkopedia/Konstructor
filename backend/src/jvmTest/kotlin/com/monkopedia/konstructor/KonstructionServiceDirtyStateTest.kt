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
package com.monkopedia.konstructor

import com.monkopedia.hauler.CallSign
import com.monkopedia.konstructor.common.DirtyState.CLEAN
import com.monkopedia.konstructor.common.DirtyState.NEEDS_EXEC
import com.monkopedia.konstructor.common.DirtyState.RENDER_FAILED
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionTarget
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.testutil.TestEnvironment
import io.ktor.utils.io.ByteReadChannel
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

/**
 * The konstruction-level [KonstructionInfo.dirtyState] after a render (#117).
 *
 * `konstruct()` used to write `dirtyState = CLEAN` unconditionally, so a konstruction whose
 * render had just failed reported itself clean — misleading on its own, and actively wrong for
 * [com.monkopedia.konstructor.hostservices.ScriptHostImpl.findScript], which reads the flag as
 * "the last build was good" to decide whether an imported konstruction needs recompiling.
 *
 * The render is driven through a [FakeController] rather than kotlinc plus a script
 * subprocess: the behaviour under test lives entirely in how [KonstructionServiceImpl] turns
 * what a render reported into dirty state, and the fake reproduces exactly the contract
 * [com.monkopedia.konstructor.tasks.ExecuteTask] produces (built targets in
 * [TaskResult.taskArguments], FAILURE as soon as any attempted target errors). That keeps
 * these in the ordinary `:backend:jvmTest` run instead of behind `-Dintegration=true`.
 *
 * Every case asserts [FakeController.renderCalls] as well as the state. Dirty-state tests in
 * this repo have passed vacuously before by never entering `render()` at all — the `:263`
 * guard skips it silently — so "the render actually ran" is part of what is being asserted,
 * not an assumption.
 */
class KonstructionServiceDirtyStateTest {

    private lateinit var env: TestEnvironment
    private lateinit var controller: FakeController
    private lateinit var service: KonstructionServiceImpl

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test")
        controller = FakeController(
            config = env.config,
            allTargets = listOf("good", "bad"),
            attemptedTargets = listOf("good", "bad"),
            info = KonstructionInfo(
                Konstruction(name = "test", workspaceId = "ws1", id = "k1"),
                // Straight out of a successful compile: the konstruction owes a render and
                // both of its targets do too, which is what lets `konstruct()` past the guard.
                dirtyState = NEEDS_EXEC,
                targets = listOf(
                    KonstructionTarget("good", NEEDS_EXEC),
                    KonstructionTarget("bad", NEEDS_EXEC)
                )
            )
        )
        installController(env.config, "ws1", "k1", controller)
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

    /**
     * The defect: a render in which a target failed to build left the konstruction CLEAN.
     */
    @Test
    fun failedRenderDoesNotLeaveTheKonstructionClean() = runBlocking {
        controller.failing += "bad"

        val result = service.konstruct("bad")

        assertEquals(1, controller.renderCalls, "the render has to have been attempted")
        assertEquals(TaskStatus.FAILURE, result.status, "the render failed. Result: $result")
        val info = service.getInfo(Unit)
        assertNotEquals(
            CLEAN,
            info.dirtyState,
            "a konstruction whose render just failed must not report itself clean. Info: $info"
        )
        assertEquals(
            RENDER_FAILED,
            info.dirtyState,
            "the failure has to be stated, not just 'not clean'. Info: $info"
        )
    }

    /**
     * The other direction: nothing failed, so the konstruction really is clean. Without this,
     * "never CLEAN" would satisfy the test above.
     */
    @Test
    fun successfulRenderLeavesTheKonstructionClean() = runBlocking {
        val result = service.konstruct("good")

        assertEquals(1, controller.renderCalls, "the render has to have been attempted")
        assertEquals(TaskStatus.SUCCESS, result.status, "the render succeeded. Result: $result")
        assertEquals(
            CLEAN,
            service.getInfo(Unit).dirtyState,
            "a render in which everything built is clean"
        )
    }

    /**
     * The per-target states #113 established are untouched: the target that built is CLEAN and
     * the one that failed stays NEEDS_EXEC, which is the retry route (#104).
     */
    @Test
    fun failedRenderKeepsThePerTargetStatesFromIssue113() = runBlocking {
        controller.failing += "bad"

        service.konstruct("bad")

        assertEquals(1, controller.renderCalls, "the render has to have been attempted")
        val targets = service.getInfo(Unit).targets.associate { it.name to it.state }
        assertEquals(CLEAN, targets["good"], "the target that built. Targets: $targets")
        assertEquals(NEEDS_EXEC, targets["bad"], "the target that failed. Targets: $targets")
    }

    /**
     * The konstruction-level state a failed render leaves must not close the retry route: the
     * `:263` guard's second clause reads the per-target states, so a re-request still has to
     * re-enter `render()`. This is what would break if the fix reached for CLEAN's opposite
     * without checking what the guard actually keys on.
     */
    @Test
    fun aFailedRenderCanStillBeRetried() = runBlocking {
        controller.failing += "bad"
        service.konstruct("bad")
        assertEquals(1, controller.renderCalls, "first render")
        assertEquals(RENDER_FAILED, service.getInfo(Unit).dirtyState, "first render failed")

        controller.failing -= "bad"
        val retry = service.konstruct("bad")

        assertEquals(2, controller.renderCalls, "the retry has to re-enter render()")
        assertEquals(TaskStatus.SUCCESS, retry.status, "the retry built. Result: $retry")
        assertEquals(
            CLEAN,
            service.getInfo(Unit).dirtyState,
            "a konstruction that has recovered goes back to clean"
        )
    }

    /**
     * Put [controller] into the process-wide [KonstructorManager] cache so the
     * [KonstructionServiceImpl] built afterwards picks it up.
     *
     * There is no injection seam — the service resolves its controller through
     * `KonstructorManager(config).controllerFor(...)` — and the cache is keyed by the [Config]
     * instance (identity: `Config` is not a data class), so a fresh [TestEnvironment] cannot
     * collide with another test's entry.
     */
    private fun installController(
        config: Config,
        workspaceId: String,
        id: String,
        controller: KonstructionController
    ) {
        val manager = KonstructorManager(config)
        val field = KonstructorManager::class.java.getDeclaredField("controllers")
        field.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val controllers =
            field.get(manager) as MutableMap<Pair<String, String>, KonstructionController>
        synchronized(controllers) {
            controllers[workspaceId to id] = controller
        }
    }
}

/**
 * A [KonstructionController] whose `render` reports what [ExecuteTask] would report, without
 * kotlinc or a script subprocess.
 *
 * Targets named in [failing] error out; the rest build. Matching the real pipeline, the STL of
 * every attempted target is deleted up front and only written back for the ones that built,
 * and the render's [TaskResult] carries the built targets in `taskArguments` with FAILURE as
 * soon as one target fails.
 */
private class FakeController(
    config: Config,
    private val allTargets: List<String>,
    private val attemptedTargets: List<String>,
    override var info: KonstructionInfo
) : KonstructionController {
    override val callSign: CallSign = CallSign("test.fake")
    override val paths: PathController.Paths = PathController(config)["ws1", "k1"]
    override val scriptLock: Mutex = Mutex()

    /** Targets that error out at execute time. Mutable so a test can un-break one. */
    val failing: MutableSet<String> = mutableSetOf()

    /** How many times `render` was entered — a render that never ran proves nothing. */
    var renderCalls: Int = 0
        private set

    private var renderResult: TaskResult? = null

    override suspend fun render(targets: List<String>): RenderedTargets {
        renderCalls++
        paths.renderOutput.mkdirs()
        val built = attemptedTargets.filter { it !in failing }
        for (target in attemptedTargets) {
            val file = File(paths.renderOutput, "$target.stl")
            file.delete()
            if (target in built) {
                file.writeText("solid $target\nendsolid $target\n")
            }
        }
        renderResult = TaskResult(
            taskArguments = built,
            status = if (built.size == attemptedTargets.size) {
                TaskStatus.SUCCESS
            } else {
                TaskStatus.FAILURE
            }
        )
        return RenderedTargets(allTargets, attemptedTargets)
    }

    override suspend fun hasRenderResult(): Boolean = renderResult != null

    override suspend fun lastRenderResult(): TaskResult = renderResult ?: error("no render")

    override suspend fun renderFile(target: String): File? =
        File(paths.renderOutput, "$target.stl").takeIf { it.exists() }

    override suspend fun read(): String = unsupported("read")

    override suspend fun write(content: String) = unsupported("write")

    override suspend fun write(content: ByteReadChannel) = unsupported("write")

    override suspend fun compile() = unsupported("compile")

    override suspend fun lastCompileResult(): TaskResult = unsupported("lastCompileResult")

    private fun unsupported(name: String): Nothing =
        throw UnsupportedOperationException("$name is not part of the render path under test")
}
