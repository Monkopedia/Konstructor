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
import com.monkopedia.konstructor.common.KonstructionCallbacks
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.testutil.FakeKonstructionListener
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream

/**
 * Black-box coverage of the bidi listener delivery path through
 * [KonstructionServiceImpl]: registration, callback filtering, multi-listener
 * fan-out, and concurrent register/unregister.
 *
 * Callbacks are dispatched asynchronously on the service's internal
 * `Dispatchers.IO` scope (`scope.launch` inside `ListenerHandler.doCallback`),
 * so assertions poll with [awaitUntil] rather than reading immediately.
 */
class KonstructionServiceListenerTest {

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

    private suspend fun awaitUntil(
        timeoutMs: Long = 5_000,
        message: String = "condition not met in time",
        condition: () -> Boolean
    ) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
        assertTrue(condition(), message)
    }

    @Test
    fun infoChangeAndContentChangeDeliveredOnSet() = runBlocking {
        val listener = FakeKonstructionListener()
        val key = service.register(listener)

        service.set("some code")

        awaitUntil(message = "expected info + content callbacks") {
            listener.infoChanges.isNotEmpty() && listener.contentChanges.isNotEmpty()
        }
        assertEquals(1, listener.infoChanges.size)
        assertEquals(1, listener.contentChanges.size)
        assertEquals(DirtyState.NEEDS_COMPILE, listener.infoChanges.first().dirtyState)

        service.unregister(key)
        Unit
    }

    @Test
    fun infoChangeDeliveredOnSetName() = runBlocking {
        val listener = FakeKonstructionListener()
        val key = service.register(listener)

        service.setName("renamed")

        awaitUntil(message = "expected info callback for rename") {
            listener.infoChanges.isNotEmpty()
        }
        assertEquals("renamed", listener.infoChanges.first().konstruction.name)

        service.unregister(key)
        Unit
    }

    @Test
    fun filteringDeliversOnlyRequestedCallbacks() = runBlocking {
        // This listener only wants TASK_COMPLETE; set() fires INFO_CHANGE and
        // CONTENT_CHANGE, neither of which should reach it.
        val listener =
            FakeKonstructionListener(callbacks = listOf(KonstructionCallbacks.TASK_COMPLETE))
        val key = service.register(listener)

        service.set("some code")

        // Give the dispatcher a chance to (incorrectly) deliver anything.
        delay(300)
        assertTrue(listener.infoChanges.isEmpty(), "INFO_CHANGE should be filtered out")
        assertTrue(listener.contentChanges.isEmpty(), "CONTENT_CHANGE should be filtered out")
        assertEquals(0, listener.totalCalls.get())

        service.unregister(key)
        Unit
    }

    @Test
    fun unregisteredListenerStopsReceivingEvents() = runBlocking {
        val listener = FakeKonstructionListener()
        val key = service.register(listener)

        service.set("first")
        awaitUntil(message = "first event not delivered") {
            listener.contentChanges.size == 1
        }

        assertTrue(service.unregister(key), "unregister should report removal")

        service.set("second")
        delay(300)
        assertEquals(1, listener.contentChanges.size, "no events should arrive after unregister")
        Unit
    }

    @Test
    fun multipleListenersAllReceiveEachEvent() = runBlocking {
        val listeners = (0 until 5).map { FakeKonstructionListener() }
        val keys = listeners.map { service.register(it) }

        service.set("code")

        listeners.forEach { listener ->
            awaitUntil(message = "every listener should see content change") {
                listener.contentChanges.isNotEmpty() && listener.infoChanges.isNotEmpty()
            }
        }
        listeners.forEach {
            assertEquals(1, it.contentChanges.size)
            assertEquals(1, it.infoChanges.size)
        }

        keys.forEach { service.unregister(it) }
        Unit
    }

    @Test
    fun oneSlowListenerDoesNotBlockOthers() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val slow = FakeKonstructionListener(
            blockUntil = mapOf(KonstructionCallbacks.CONTENT_CHANGE to gate)
        )
        val fast = FakeKonstructionListener()
        val slowKey = service.register(slow)
        val fastKey = service.register(fast)

        service.set("code")

        // The fast listener completes even while the slow one is parked in its
        // CONTENT_CHANGE callback, proving fan-out is not serialized.
        awaitUntil(message = "fast listener blocked by slow listener") {
            fast.contentChanges.isNotEmpty()
        }
        // Poll rather than assert immediately: the slow listener records its
        // content change before parking on the gate, but its coroutine may not
        // have been scheduled yet when the fast listener finished (a timing race
        // that flaked under CI load). awaitUntil still proves fan-out isn't
        // serialized — a serialized impl would hang the fast awaitUntil above.
        awaitUntil(message = "slow listener should have started its callback") {
            slow.contentChanges.isNotEmpty()
        }

        // Release the slow listener and clean up.
        gate.complete(Unit)
        service.unregister(slowKey)
        service.unregister(fastKey)
        Unit
    }

    @Test
    fun concurrentRegisterUnregisterDoesNotLeakOrThrow() = runBlocking {
        withTimeout(15_000) {
            coroutineScope {
                // Hammer register/unregister while events are being broadcast.
                val churn = (0 until 50).map { i ->
                    launch {
                        val l = FakeKonstructionListener()
                        val k = service.register(l)
                        delay((i % 5).toLong())
                        service.unregister(k)
                    }
                }
                val events = (0 until 25).map {
                    launch {
                        service.set("content-$it")
                    }
                }
                (churn + events).forEach { it.join() }
            }
        }

        // After the storm, a fresh listener must still work end to end and no
        // stale listeners may remain wired up.
        val survivor = FakeKonstructionListener()
        val key = service.register(survivor)
        service.set("final")
        awaitUntil(message = "service unusable after concurrent churn") {
            survivor.contentChanges.isNotEmpty()
        }

        assertTrue(service.unregister(key), "survivor should unregister cleanly")
        assertFalse(service.unregister(key), "double unregister should be false (no leak)")
        Unit
    }
}
