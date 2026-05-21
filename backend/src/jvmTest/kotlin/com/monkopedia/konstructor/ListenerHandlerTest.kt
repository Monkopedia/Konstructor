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
@file:OptIn(ExperimentalSerializationApi::class, ExperimentalCoroutinesApi::class)

package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionCallbacks
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.common.KonstructionTarget
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.testutil.FakeKonstructionListener
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream

/**
 * White-box coverage of [ListenerHandler] focused on the failure / lifecycle
 * behaviour that is hard to observe through the public service surface:
 *  - throwing callbacks are caught, the listener is closed and unregistered;
 *  - a callback that never returns (dead client) hits the 120s `withTimeout`
 *    and is then closed/unregistered;
 *  - callback ordering for an info change matches the documented sequence.
 *
 * [ListenerHandler] takes its dispatch [CoroutineScope] as a constructor
 * argument, so these tests inject the [runTest] scope. That makes the 120s
 * timeout testable in virtual time — we advance the clock past 120s with
 * [advanceTimeBy] instead of actually waiting.
 *
 * To exercise the close path end to end (`ListenerHandler.close()` ->
 * `service.unregister(key)` -> `listener.close()`) the listener is first
 * registered with a real [KonstructionServiceImpl] so the service holds it
 * under [key]; the test handler is then constructed with that same key and the
 * test scope. Firing a callback on the test handler drives the production
 * close/unregister logic against the real service map.
 */
class ListenerHandlerTest {

    private lateinit var env: TestEnvironment
    private lateinit var service: KonstructionServiceImpl

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test")
        val dir = File(env.tempDir, "ws1/k1")
        dir.mkdirs()
        val info = KonstructionInfo(
            Konstruction(name = "test", workspaceId = "ws1", id = "k1"),
            DirtyState.CLEAN
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

    @AfterTest
    fun tearDown() {
        env.close()
    }

    private fun sampleInfo(dirtyState: DirtyState = DirtyState.NEEDS_COMPILE) = KonstructionInfo(
        Konstruction(name = "test", workspaceId = "ws1", id = "k1"),
        dirtyState
    )

    @Test
    fun throwingCallbackIsCaughtClosedAndUnregistered() = runTest {
        val listener = FakeKonstructionListener(throwOn = KonstructionCallbacks.INFO_CHANGE)
        // Register so the service holds the listener under `key`; the close path
        // relies on the key being present in the service map.
        val key = service.register(listener)

        val handler = ListenerHandler(
            key,
            service,
            CoroutineScope(coroutineContext),
            listener
        ).also {
            it.init()
        }

        handler.onInfoChanged(sampleInfo(), changedTargets = emptyList())
        advanceUntilIdle()

        // The throw was caught (no exception propagated to the test) and the
        // listener was closed and unregistered.
        assertEquals(1, listener.infoChanges.size, "callback should have run once")
        assertTrue(listener.closed, "listener should have been close()d after throwing")
        assertFalse(
            service.unregister(key),
            "listener should already be unregistered (second unregister is false)"
        )

        // Subsequent events on the now-dead handler must not redeliver to the
        // (closed) listener path in a way that re-registers it.
        handler.onContentChange()
        advanceUntilIdle()
        assertFalse(service.unregister(key), "no re-registration should occur")
    }

    /**
     * Dead-client / 120s timeout path.
     *
     * BUG DOCUMENTED (NOT FIXED HERE — per task constraints): when a callback
     * hangs, `withTimeout(120.seconds)` cancels the callback body. The
     * `catch (t: Throwable)` in `ListenerHandler.doCallback` does catch the
     * resulting `TimeoutCancellationException`, but it then calls the
     * *suspending* `close()` -> `service.unregister(key)` *inside the
     * already-cancelled `withTimeout` scope*. Because the coroutine is in the
     * cancelling state, that suspension throws `CancellationException`
     * immediately, so `unregister`/`listener.close()` never actually run. The
     * timed-out listener therefore stays registered and leaks — exactly the
     * "silent error visible only in logs" the issue describes (the 2026-05-19
     * dump showed repeated 120s timeouts for listeners that were never cleaned
     * up).
     *
     * After the fix (cleanup runs in a NonCancellable context outside the
     * timed-out scope) the timeout fires AND the listener is closed and
     * unregistered, so the dead listener no longer leaks.
     */
    @Test
    fun deadClientCallbackTimesOutAndIsCleanedUp() = runTest {
        val neverCompletes = CompletableDeferred<Unit>()
        val listener = FakeKonstructionListener(
            blockUntil = mapOf(KonstructionCallbacks.CONTENT_CHANGE to neverCompletes)
        )
        val key = service.register(listener)

        val handler = ListenerHandler(
            key,
            service,
            CoroutineScope(coroutineContext),
            listener
        ).also {
            it.init()
        }

        handler.onContentChange()
        // Let the callback start and park in its blocking await WITHOUT advancing
        // the clock (advanceUntilIdle would jump past the 120s timeout).
        runCurrent()
        assertEquals(1, listener.contentChanges.size, "callback should have started")
        assertFalse(listener.closed, "should not be closed before the timeout elapses")

        // Cross the 120s boundary in virtual time -> withTimeout cancels the
        // callback body, and the NonCancellable cleanup closes + unregisters.
        advanceTimeBy(121.seconds)
        advanceUntilIdle()

        // The timed-out listener is closed and removed from the service map.
        assertTrue(
            listener.closed,
            "timed-out listener should be close()d by the NonCancellable cleanup"
        )
        assertFalse(
            service.unregister(key),
            "timed-out listener should already be unregistered"
        )
    }

    @Test
    fun infoChangeCallbacksFireInDocumentedOrder() = runTest {
        // onInfoChanged with dirtyChanged=true and a changed target should emit
        // DIRTY_CHANGE, then TARGET_CHANGE (per changed target), then INFO_CHANGE.
        val listener = FakeKonstructionListener()
        val key = service.register(listener)
        val handler = ListenerHandler(
            key,
            service,
            CoroutineScope(coroutineContext),
            listener
        ).also {
            it.init()
        }

        val target = KonstructionTarget("main", DirtyState.NEEDS_COMPILE)
        handler.onInfoChanged(
            sampleInfo(),
            changedTargets = listOf(target),
            dirtyChanged = true
        )
        advanceUntilIdle()

        assertContentEquals(
            listOf(
                KonstructionCallbacks.DIRTY_CHANGE,
                KonstructionCallbacks.TARGET_CHANGE,
                KonstructionCallbacks.INFO_CHANGE
            ),
            listener.callOrder,
            "info change should emit dirty, then target, then info"
        )

        service.unregister(key)
    }

    @Test
    fun filteredHandlerSkipsUnrequestedCallbacks() = runTest {
        val listener =
            FakeKonstructionListener(callbacks = listOf(KonstructionCallbacks.TASK_COMPLETE))
        val key = service.register(listener)
        val handler = ListenerHandler(
            key,
            service,
            CoroutineScope(coroutineContext),
            listener
        ).also {
            it.init()
        }

        handler.onInfoChanged(
            sampleInfo(),
            changedTargets = listOf(KonstructionTarget("main", DirtyState.NEEDS_COMPILE)),
            dirtyChanged = true
        )
        handler.onContentChange()
        handler.onTaskComplete(TaskResult(status = TaskStatus.SUCCESS))
        advanceUntilIdle()

        assertEquals(1, listener.totalCalls.get(), "only the requested callback should fire")
        assertEquals(1, listener.taskCompletes.size)
        assertTrue(listener.infoChanges.isEmpty())
        assertTrue(listener.dirtyChanges.isEmpty())
        assertTrue(listener.targetChanges.isEmpty())
        assertTrue(listener.contentChanges.isEmpty())

        service.unregister(key)
    }

    @Test
    fun eachCallbackTypeIsDeliveredWhenRequested() = runTest {
        val listener = FakeKonstructionListener()
        val key = service.register(listener)
        val handler = ListenerHandler(
            key,
            service,
            CoroutineScope(coroutineContext),
            listener
        ).also {
            it.init()
        }

        handler.onContentChange()
        handler.onTaskComplete(TaskResult(status = TaskStatus.SUCCESS))
        handler.onRenderChanged(
            com.monkopedia.konstructor.common.KonstructionRender(
                Konstruction(name = "test", workspaceId = "ws1", id = "k1"),
                "main",
                "model/path"
            )
        )
        handler.onInfoChanged(
            sampleInfo(),
            changedTargets = listOf(KonstructionTarget("main", DirtyState.NEEDS_COMPILE)),
            dirtyChanged = true
        )
        advanceUntilIdle()

        assertEquals(1, listener.contentChanges.size, "CONTENT_CHANGE")
        assertEquals(1, listener.taskCompletes.size, "TASK_COMPLETE")
        assertEquals(1, listener.renderChanges.size, "RENDER_CHANGE")
        assertEquals(1, listener.infoChanges.size, "INFO_CHANGE")
        assertEquals(1, listener.dirtyChanges.size, "DIRTY_CHANGE")
        assertEquals(1, listener.targetChanges.size, "TARGET_CHANGE")

        service.unregister(key)
    }
}
