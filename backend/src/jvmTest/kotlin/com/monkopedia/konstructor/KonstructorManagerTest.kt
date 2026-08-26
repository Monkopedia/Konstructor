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
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.fail
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.junit.After
import org.junit.Before

class KonstructorManagerTest {

    private lateinit var env: TestEnvironment
    private lateinit var manager: KonstructorManager

    @Before
    fun setUp() {
        env = TestEnvironment()
        manager = KonstructorManager(env.config)
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
    fun testSameIdReturnsSameController() {
        env.createWorkspaceDir("ws1", "Workspace 1")
        createKonstructionInfo("ws1", "k1", "test-k")
        val controller1 = manager.controllerFor("ws1", "k1")
        val controller2 = manager.controllerFor("ws1", "k1")
        assertSame(controller1, controller2)
    }

    /**
     * The controller cache must survive garbage collection.
     *
     * `contentFileLock` and `scriptLock` are per-instance fields of
     * `KonstructionControllerImpl`, and callers reach them *through* this cache
     * (`ScriptManager`, `ScriptHostImpl`). If the cache entry can disappear, two callers
     * working on the same konstruction get two different `Mutex` instances and the mutual
     * exclusion silently evaporates.
     *
     * Note `controller1` is held strongly for the whole test: the point is that the
     * *entry* must survive, not merely the value. A weak-**key** map loses the entry even
     * while the value is strongly reachable, because nothing outside the map ever holds
     * the freshly allocated key.
     *
     * [forceWeakKeyCollection] fails the test if the JVM never actually collects, so a
     * pass here cannot mean "the GC simply never ran" — the flaw that made
     * [testSameIdReturnsSameController] unable to fail.
     */
    @Test
    fun testSameControllerAfterGarbageCollection() {
        env.createWorkspaceDir("ws1", "Workspace 1")
        createKonstructionInfo("ws1", "k1", "test-k")
        val controller1 = manager.controllerFor("ws1", "k1")

        forceWeakKeyCollection()

        val controller2 = manager.controllerFor("ws1", "k1")
        assertSame(
            controller1,
            controller2,
            "Controller cache dropped its entry across a GC; per-konstruction locks " +
                "(contentFileLock/scriptLock) are per-instance, so callers would now be " +
                "locking different Mutex instances for the same konstruction."
        )
        assertSame(
            controller1.scriptLock,
            controller2.scriptLock,
            "scriptLock identity is the invariant that actually matters."
        )
    }

    /**
     * Positive control for the GC assertion above.
     *
     * Builds a canary entry with exactly the reachability shape of the cache under test —
     * a weakly held key that nothing outside the map references — and drives GC until it
     * is observably cleared. Returning normally means weak keys *were* collected in this
     * JVM at this moment; if that never happens within the budget the test fails rather
     * than proceeding to an assertion that could only pass vacuously.
     */
    private fun forceWeakKeyCollection() {
        val canaryMap = WeakHashMap<Any, Any>()
        val canaryRef = allocateCanary(canaryMap)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GC_BUDGET_SECONDS)
        var attempts = 0
        while (System.nanoTime() < deadline) {
            attempts++
            System.gc()
            if (canaryRef.get() == null && canaryMap.isEmpty()) return
            // Allocation pressure: System.gc() is advisory, so give the collector a
            // reason as well as a request.
            @Suppress("UNUSED_VARIABLE")
            val ballast = ByteArray(BALLAST_BYTES)
            Thread.sleep(GC_POLL_MILLIS)
        }
        fail(
            "GC never cleared a weakly-keyed canary entry after $attempts attempts in " +
                "${GC_BUDGET_SECONDS}s; the identity assertion would have been vacuous."
        )
    }

    /**
     * Allocated in its own frame so the only reference to the canary dies with that frame
     * (a local in the calling method can otherwise stay live in a stack slot). The value
     * is a constant String, never anything that refers back to the key.
     */
    private fun allocateCanary(map: WeakHashMap<Any, Any>): WeakReference<Any> {
        val canary = Any()
        map[canary] = CANARY_VALUE
        return WeakReference(canary)
    }

    @Test
    fun testDifferentIdReturnsDifferentController() {
        env.createWorkspaceDir("ws1", "Workspace 1")
        createKonstructionInfo("ws1", "k1", "test-k1")
        createKonstructionInfo("ws1", "k2", "test-k2")
        val controller1 = manager.controllerFor("ws1", "k1")
        val controller2 = manager.controllerFor("ws1", "k2")
        assertNotSame(controller1, controller2)
    }

    companion object {
        private const val CANARY_VALUE = "canary"
        private const val GC_BUDGET_SECONDS = 30L
        private const val GC_POLL_MILLIS = 20L
        private const val BALLAST_BYTES = 4 * 1024 * 1024
    }
}
