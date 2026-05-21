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

import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.TaskStatus.SUCCESS
import com.monkopedia.konstructor.hostservices.ScriptManager
import com.monkopedia.konstructor.tasks.ExecuteTask
import com.monkopedia.konstructor.testutil.TestEnvironment
import com.sun.management.UnixOperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Opt-in soak test for the compile/render subprocess pipeline (konstructor#4).
 *
 * Runs a realistic edit/save/compile/render cycle in a loop for a bounded
 * duration, snapshotting the JVM's thread set, heap usage and open file
 * descriptors at the start and end. Asserts the host process does not leak
 * resources across many subprocess lifecycles — the class of bug (lock left
 * held, watcher coroutines/threads piling up, fds not closed) that only shows
 * up under sustained activity, like the 2026-05-19 adolin deadlock (#1).
 *
 * Disabled by default. Run with:
 *
 *     JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
 *       ./gradlew shadowJar :backend:jvmTest -Psoak -Pduration=30 \
 *       --tests "*.KonstructionControllerSoakTest"
 *
 * `-Pduration` is in minutes (default 5 when -Psoak is set without it). Because
 * each cycle forks real `kotlinc`/`kotlin` processes, the full build (lib
 * shadowJar bundled as `lib-all.raj`) must be present.
 */
@OptIn(ExperimentalSerializationApi::class)
class KonstructionControllerSoakTest {

    private var env: TestEnvironment? = null
    private var durationMinutes: Long = 5

    @Before
    fun setUp() {
        assumeTrue(
            "Set -Psoak (or -Dsoak=true) to run the soak test",
            System.getProperty("soak") != null
        )
        durationMinutes = System.getProperty("duration")?.toLongOrNull() ?: 5
        env = TestEnvironment()
        // LibsJar statically caches the extracted lib.jar path across Config
        // instances; re-extract into this test's live dataDir.
        TestEnvironment.resetLibsJarCache()
        com.monkopedia.konstructor.tasks.LibsJar.getLibsJar(env!!.config)
        env!!.createWorkspaceDir(WS, "Soak")
        createKonstructionInfo(WS, K, "soak-k")
    }

    @After
    fun tearDown() {
        env?.close()
    }

    @Test
    fun repeatedCompileRenderCyclesDoNotLeakResources(): Unit = runBlocking {
        val durationMs = durationMinutes * 60_000
        val before = snapshot()
        logSnapshot("BEFORE", before)

        var cycles = 0
        val deadline = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < deadline) {
            // Vary the content each cycle to mimic a real edit/save/build loop.
            val script = scriptForCycle(cycles)
            runCycle(script)
            cycles++
        }
        assertTrue(cycles > 0, "Soak ran zero cycles")
        println("[soak] completed $cycles compile/render cycles over $durationMinutes min")

        // Encourage cleanup of anything genuinely unreferenced before measuring,
        // and give async exit-watchers a moment to unwind.
        System.gc()
        Thread.sleep(2_000)
        System.gc()

        val after = snapshot()
        logSnapshot("AFTER", after)

        // Thread count: allow generous slack for daemon/GC threads but catch a
        // per-cycle leak (would be on the order of `cycles`).
        val threadGrowth = after.threadCount - before.threadCount
        assertTrue(
            threadGrowth <= THREAD_GROWTH_LIMIT,
            "Thread count grew by $threadGrowth (before=${before.threadCount}, " +
                "after=${after.threadCount}) over $cycles cycles — likely a leaked " +
                "per-render thread/coroutine pool"
        )

        // No host thread should be sitting blocked on a monitor at rest. A leaked
        // locked monitor is the fingerprint of the adolin deadlock.
        assertTrue(
            after.blockedThreads.isEmpty(),
            "Threads still BLOCKED on a monitor at rest: ${after.blockedThreads}"
        )

        // File descriptors: must not grow ~1:1 with cycles (unclosed pipes).
        if (before.openFds >= 0 && after.openFds >= 0) {
            val fdGrowth = after.openFds - before.openFds
            assertTrue(
                fdGrowth <= FD_GROWTH_LIMIT,
                "Open file descriptors grew by $fdGrowth (before=${before.openFds}, " +
                    "after=${after.openFds}) over $cycles cycles — likely leaked " +
                    "subprocess pipes"
            )
        }

        // Heap: tolerate steady-state churn but fail on monotonic, unbounded
        // growth far larger than a single render's working set.
        val heapGrowthMb = (after.usedHeap - before.usedHeap) / (1024 * 1024)
        assertTrue(
            heapGrowthMb <= HEAP_GROWTH_LIMIT_MB,
            "Used heap grew by ${heapGrowthMb}MB (before=${before.usedHeap / 1024 / 1024}MB, " +
                "after=${after.usedHeap / 1024 / 1024}MB) over $cycles cycles"
        )

        // Sanity: the controller is still usable after the soak.
        val finalResult = controller().lastRenderResult()
        assertNotNull(finalResult, "Controller produced no render result after soak")
    }

    // --- one realistic edit/save/compile/render cycle --------------------------

    private suspend fun runCycle(script: String) {
        val c = controller()
        c.write(script)
        c.compile()
        val compileResult = c.lastCompileResult()
        assertEquals(SUCCESS, compileResult.status, "Soak compile failed: $compileResult")
        renderViaController()
    }

    private suspend fun renderViaController() {
        // Drive the same ScriptManager/ExecuteTask path the controller uses so
        // the per-konstruction scriptLock and subprocess lifecycle are exercised.
        val paths = paths()
        paths.renderOutput.mkdirs()
        val script = ScriptManager(config()).getScript(paths, "soak-k")
        val (result, _) = ExecuteTask(config(), script, extraTargets = emptyList()).execute()
        paths.renderResultFile.outputStream().use { out ->
            config().json.encodeToStream(result, out)
        }
        assertEquals(SUCCESS, result.status, "Soak render failed: $result")
    }

    private fun scriptForCycle(i: Int): String {
        val dim = 5.0 + (i % 5)
        return """
            val cube$i by primitive {
                cube {
                    dimensions = xyz($dim, $dim, $dim)
                }
            }
            export("cube$i")
        """.trimIndent()
    }

    // --- resource snapshots ----------------------------------------------------

    private data class Snap(
        val threadCount: Int,
        val blockedThreads: List<String>,
        val usedHeap: Long,
        val openFds: Long
    )

    private fun snapshot(): Snap {
        val threadBean = ManagementFactory.getThreadMXBean()
        val infos = threadBean.dumpAllThreads(false, false)
        val blocked = infos.filter {
            it.threadState == Thread.State.BLOCKED
        }.map { "${it.threadName}#${it.threadId} on ${it.lockName}" }

        val memBean = ManagementFactory.getMemoryMXBean()
        val usedHeap = memBean.heapMemoryUsage.used

        val osBean = ManagementFactory.getOperatingSystemMXBean()
        val openFds = if (osBean is UnixOperatingSystemMXBean) {
            osBean.openFileDescriptorCount
        } else {
            -1
        }
        return Snap(infos.size, blocked, usedHeap, openFds)
    }

    private fun logSnapshot(label: String, s: Snap) {
        println(
            "[soak][$label] threads=${s.threadCount} blocked=${s.blockedThreads.size} " +
                "heapUsed=${s.usedHeap / 1024 / 1024}MB openFds=${s.openFds}"
        )
        if (s.blockedThreads.isNotEmpty()) {
            println("[soak][$label] blocked: ${s.blockedThreads}")
        }
    }

    // --- helpers ---------------------------------------------------------------

    private fun config(): Config = env!!.config

    private fun controller() = KonstructorManager(config()).controllerFor(WS, K)

    private fun paths(): PathController.Paths = PathController(config())[WS, K]

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
        private const val WS = "ws-soak"
        private const val K = "k-soak"

        // Slack tuned to catch per-cycle leaks while tolerating JVM/GC daemon
        // threads. A real leak grows ~linearly with cycle count (hundreds+).
        private const val THREAD_GROWTH_LIMIT = 30
        private const val FD_GROWTH_LIMIT = 64L
        private const val HEAP_GROWTH_LIMIT_MB = 512L
    }
}
