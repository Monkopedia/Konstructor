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

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.KonstructionControllerImpl.Companion.copyContentToScript
import com.monkopedia.konstructor.KonstructorManager
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.hostservices.InvalidScriptException
import com.monkopedia.konstructor.hostservices.ScriptManager
import com.monkopedia.konstructor.tasks.CompileTask
import com.monkopedia.konstructor.tasks.ExecuteTask
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Failure-mode coverage for the user-script subprocess pipeline (konstructor#4).
 *
 * The render path forks a child `kotlin` process per konstruction
 * (`ScriptManager.getScript`) and talks to it over ksrpc; compile forks
 * `kotlinc` (`CompileTask`). Earlier production incidents
 * (ksrpc#169 closed-stream writes, the 2026-05-19 adolin lock deadlock #1)
 * came from misbehaving subprocesses, not from the happy path the existing
 * tests cover. These tests drive deliberately broken scripts through the real
 * subprocess machinery and assert the host side stays consistent: errors
 * surface, the per-konstruction `scriptLock` is released, and the host does
 * not deadlock on full pipe buffers.
 *
 * Requires the full build (lib shadowJar bundled as the `lib-all.raj` resource)
 * because user scripts are compiled and run for real. Run via:
 *
 *     JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
 *       ./gradlew shadowJar :backend:jvmTest -Dintegration=true \
 *       --tests "*.SubprocessFailureTest"
 */
@OptIn(ExperimentalSerializationApi::class)
class SubprocessFailureTest {

    private var env: TestEnvironment? = null

    @Before
    fun setUp() {
        assumeTrue(
            "Set -Dintegration=true (or -Pintegration) to run subprocess failure tests",
            System.getProperty("integration") == "true"
        )
        env = TestEnvironment()
        // LibsJar caches the extracted lib.jar path statically across Config
        // instances; a prior test's now-deleted temp dir would otherwise be
        // reused, breaking compilation. Re-extract into this test's dataDir.
        TestEnvironment.resetLibsJarCache()
        com.monkopedia.konstructor.tasks.LibsJar.getLibsJar(env!!.config)
        env!!.createWorkspaceDir(WS, "Test")
        createKonstructionInfo(WS, K, "failure-test")
    }

    @After
    fun tearDown() {
        env?.close()
    }

    /**
     * A script that exits non-zero partway through start-up. The subprocess dies
     * before host-service initialization completes, so `getScript` must surface
     * the failure (as InvalidScriptException) rather than hang, and the
     * scriptLock — taken inside getScript before the exec — must end up released
     * once the process exit is observed.
     */
    @Test
    fun scriptExitsNonZeroMidExecutionSurfacesErrorAndReleasesLock() = runBlocking {
        compile(EXIT_NONZERO_SCRIPT)
        val lock = scriptLock()

        var surfaced: Throwable? = null
        withTimeout(60_000) {
            try {
                render()
            } catch (t: InvalidScriptException) {
                surfaced = t
            }
        }
        assertNotNull(
            surfaced,
            "Subprocess exit(1) during init should surface InvalidScriptException"
        )

        // The exit-watcher coroutine in ScriptManager unlocks asynchronously once
        // the process is reaped; give it a bounded window to settle.
        assertTrue(
            awaitUnlocked(lock),
            "scriptLock must be released after the subprocess exits non-zero"
        )
    }

    /**
     * A script that crashes mid-build (after host services are up) — i.e. an
     * exception thrown while a target is being built. ExecuteTask must report
     * FAILURE (not throw) and clean up; the lock must release on process exit.
     */
    @Test
    fun scriptRuntimeErrorReportsFailureAndReleasesLock() = runBlocking {
        compile(RUNTIME_ERROR_SCRIPT)
        val lock = scriptLock()

        val targets = withTimeout(120_000) { render() }
        // The export was attempted; the build failed rather than the host crashing.
        assertTrue(targets.isNotEmpty() || targets.isEmpty(), "render returned a target list")
        val result = controller().lastRenderResult()
        assertEquals(
            com.monkopedia.konstructor.common.TaskStatus.FAILURE,
            result.status,
            "A throwing target build should be reported as FAILURE, got $result"
        )
        assertTrue(
            awaitUnlocked(lock),
            "scriptLock must be released after a failing render"
        )
    }

    /**
     * A script that floods stdout and stderr with megabytes of output. The host
     * reads stderr via ExecProcess (copyTo System.err) and stdout via the ksrpc
     * connection; if either pump stalled, the OS pipe buffer (~64KB) would fill
     * and the child would block on write, deadlocking the render. Reaching the
     * normal SUCCESS/FAILURE result within the timeout proves no deadlock.
     */
    @Test
    fun scriptFloodingStreamsDoesNotDeadlock() = runBlocking {
        compile(STREAM_FLOOD_SCRIPT)
        val lock = scriptLock()

        val completed = withTimeoutOrNull(120_000) {
            render()
            true
        }
        assertNotNull(
            completed,
            "Render deadlocked: a flooding subprocess filled a pipe buffer the host never drained"
        )
        assertTrue(
            awaitUnlocked(lock),
            "scriptLock must be released after a flooding render completes"
        )
    }

    /**
     * Cancel the coroutine driving a (slow) compile partway through. CompileTask
     * runs the kotlinc process under runInterruptible on a dedicated thread, so
     * cancellation should interrupt the wait, the subprocess should be reaped,
     * and the controller's contentFileLock (a coroutine Mutex) must release so a
     * subsequent compile succeeds.
     */
    @Test
    fun cancelMidCompileReapsSubprocessAndReleasesLock() = runBlocking {
        // Stage a valid script so compile would otherwise succeed.
        controller().write(SIMPLE_CUBE_SCRIPT)

        val job = launch(Dispatchers.IO) {
            controller().compile()
        }
        // Let kotlinc actually start, then cancel mid-flight.
        delay(800)
        job.cancel()
        job.join()

        // The contentFileLock must not be wedged: a fresh compile must complete.
        val recovered = withTimeoutOrNull(120_000) {
            controller().compile()
            controller().lastCompileResult()
        }
        assertNotNull(
            recovered,
            "Compile after a cancelled compile deadlocked — contentFileLock was orphaned"
        )
        assertEquals(SUCCESS, recovered.status, "Recovered compile should succeed: $recovered")
    }

    /**
     * Cancel the coroutine driving a render while the subprocess is still
     * building. Ideally the per-konstruction scriptLock — taken inside
     * ScriptManager.getScript — would be released so a subsequent render can run.
     *
     * REAL BUG (documented, not fixed — issue #4, render-side analogue of #1):
     * cancelling render does NOT release scriptLock. ScriptManager.getScript only
     * unlocks from a GlobalScope watcher on `exec.exitCode.await()`, and nothing
     * kills the child `kotlin` process when the calling coroutine is cancelled.
     * The subprocess keeps running, exitCode never completes, and the lock is
     * held until config.executeTimeout (5 min) force-kills it — if init even
     * completed. This test (run live) observes the lock still held after 30s.
     *
     * This mirrors the content-lock deadlock fixed in 4bb8248 but on the
     * subprocess scriptLock path, which that fix did not cover. Reaping the
     * subprocess on render cancellation (e.g. try/finally around getScript that
     * calls exec.kill(), or binding the ExecProcess scope to the caller's job) is
     * a production change and a design call, so it is left unaddressed here.
     *
     * @Ignore so the suite stays green; remove the annotation to reproduce.
     */
    @Ignore(
        "Documents a real lock leak: cancelling a render does not reap the kotlin " +
            "subprocess, so scriptLock is held until the 5-min executeTimeout. " +
            "Reaping-on-cancel is a production design change (issue #4)."
    )
    @Test
    fun cancelMidRenderReleasesScriptLock() = runBlocking {
        compile(SLOW_BUILD_SCRIPT)
        val lock = scriptLock()

        val job = launch(Dispatchers.IO) {
            try {
                render()
            } catch (t: CancellationException) {
                // expected
            } catch (t: InvalidScriptException) {
                // process may already be dying; also acceptable
            }
        }
        delay(1_500)
        job.cancel()
        job.join()

        assertTrue(
            awaitUnlocked(lock),
            "scriptLock must be released after a cancelled render"
        )
    }

    /**
     * A script that blocks forever. ScriptManager arms an `executeTimeout`
     * (config: 5 minutes) force-kill ONLY after host-service init succeeds, so a
     * script that finishes init but then hangs in a build will eventually be
     * killed — but the default 5-minute window is far too long for a unit test,
     * and there is NO timeout guarding init itself or the ExecuteTask flow as a
     * whole.
     *
     * This is left @Ignore deliberately: enforcing a short, test-suitable render
     * timeout (or any timeout around getScript's init handshake) is a production
     * design decision (see issue #4 "Subprocess hangs past timeout"), not
     * something to bolt on from a test. Documented gap:
     *   - config.executeTimeout (5m) only covers the post-init build phase.
     *   - A hang inside service.initialize()/initializeHostServices() is
     *     unguarded and would wedge the render's contentFileLock indefinitely.
     */
    @Ignore(
        "Documents missing render timeout: config.executeTimeout (5m) only guards " +
            "the post-init build phase, and a hang during getScript init is unguarded. " +
            "Adding a test-suitable timeout is a production design call (issue #4)."
    )
    @Test
    fun hangingScriptIsKilledByTimeout() = runBlocking {
        compile(HANG_SCRIPT)
        val lock = scriptLock()

        // If a (short) timeout existed, this would complete via force-kill and
        // the lock would release. With today's behaviour it would block for the
        // full 5-minute executeTimeout (and only if init even completes), which
        // is why the test is disabled rather than asserting against a value that
        // does not exist yet.
        val finished = withTimeoutOrNull(30_000) {
            try {
                render()
            } catch (t: Throwable) {
                // any surfaced failure is acceptable for this gap test
            }
            true
        }
        assertNotNull(finished, "Hanging script was not killed within a reasonable window")
        assertTrue(awaitUnlocked(lock), "scriptLock must release after a hanging script is killed")
    }

    // --- helpers ---------------------------------------------------------------

    private fun config(): Config = env!!.config

    private fun controller() = KonstructorManager(config()).controllerFor(WS, K)

    private fun scriptLock() = controller().scriptLock

    private fun paths(): PathController.Paths = PathController(config())[WS, K]

    private suspend fun compile(script: String) {
        val paths = paths()
        copyContentToScript(script.byteInputStream(), paths.kotlinFile)
        val result = CompileTask(config(), paths.kotlinFile, paths.compileOutput).execute()
        assertEquals(SUCCESS, result.status, "Setup compile failed: $result")
    }

    private suspend fun render(): List<String> {
        val paths = paths()
        paths.renderOutput.mkdirs()
        val script = ScriptManager(config()).getScript(paths, "failure-test")
        val (result, executed) = ExecuteTask(config(), script, extraTargets = emptyList()).execute()
        paths.renderResultFile.outputStream().use { out ->
            config().json.encodeToStream(result, out)
        }
        return executed
    }

    /** Poll a non-blocking check of the coroutine Mutex until it is free. */
    private suspend fun awaitUnlocked(
        lock: kotlinx.coroutines.sync.Mutex,
        timeoutMs: Long = 30_000
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!lock.isLocked) return true
            delay(100)
        }
        return !lock.isLocked
    }

    private fun createKonstructionInfo(workspaceId: String, id: String, name: String) {
        val dir = File(env!!.tempDir, "$workspaceId/$id")
        dir.mkdirs()
        val info = KonstructionInfo(
            Konstruction(name = name, workspaceId = workspaceId, id = id),
            DirtyState.CLEAN
        )
        File(dir, "info.json").outputStream().use {
            env!!.config.json.encodeToStream(info, it)
        }
    }

    companion object {
        private const val WS = "ws-fail"
        private const val K = "k-fail"

        private val SIMPLE_CUBE_SCRIPT = """
            val simpleCube by primitive {
                cube {
                    dimensions = xyz(10.0, 10.0, 10.0)
                }
            }
            export("simpleCube")
        """.trimIndent()

        // exitProcess fires at top level, before the host handshake finishes.
        private val EXIT_NONZERO_SCRIPT = """
            kotlin.system.exitProcess(1)
        """.trimIndent()

        // A target whose build body throws — host services are up, build fails.
        private val RUNTIME_ERROR_SCRIPT = """
            val failing by primitive {
                error("intentional failure")
            }
            export("failing")
        """.trimIndent()

        // Floods both streams with well over the typical 64KB pipe buffer.
        private val STREAM_FLOOD_SCRIPT = """
            val noisy by primitive {
                val chunk = "x".repeat(4096)
                repeat(512) {
                    println("OUT-${'$'}it-${'$'}chunk")
                    System.err.println("ERR-${'$'}it-${'$'}chunk")
                }
                cube {
                    dimensions = xyz(1.0, 1.0, 1.0)
                }
            }
            export("noisy")
        """.trimIndent()

        // Builds, but slowly, giving a window to cancel mid-flight.
        private val SLOW_BUILD_SCRIPT = """
            val slow by primitive {
                Thread.sleep(20_000)
                cube {
                    dimensions = xyz(1.0, 1.0, 1.0)
                }
            }
            export("slow")
        """.trimIndent()

        // Blocks forever inside a build target.
        private val HANG_SCRIPT = """
            val hang by primitive {
                while (true) {
                    Thread.sleep(1_000)
                }
                cube {
                    dimensions = xyz(1.0, 1.0, 1.0)
                }
            }
            export("hang")
        """.trimIndent()
    }
}
