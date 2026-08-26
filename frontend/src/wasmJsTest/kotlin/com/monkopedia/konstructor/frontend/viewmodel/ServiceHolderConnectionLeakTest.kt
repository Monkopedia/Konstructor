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
package com.monkopedia.konstructor.frontend.viewmodel

import com.monkopedia.hauler.Shipper
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The reconnect loop allocates an [io.ktor.client.HttpClient] and a websocket
 * for every attempt. Nothing about those objects is observable from outside, so
 * these tests substitute a [ConnectionFactory] that hands out counted stand-ins
 * and assert on the count that matters: how many connections have been
 * allocated and never closed.
 *
 * The assertion that carries the weight is `live == 0`. Against the pre-fix loop
 * (no `finally { connection?.closeQuietly() }`) `live` grows by one per attempt.
 */
class ServiceHolderConnectionLeakTest {

    @Test
    fun failedAttemptsDoNotAccumulateOpenConnections() = runTest {
        // Every attempt gets as far as allocating a connection and then fails
        // its first ping — the "backend is down" case.
        val factory = CountingConnectionFactory(successfulPingsPerConnection = 0)
        ServiceHolder(factory, backgroundScope)

        // NB: advanceTimeBy (not advanceUntilIdle) — the holder runs in
        // backgroundScope, and advanceUntilIdle only drains foreground work.
        advanceTimeBy(30_001)
        runCurrent()

        // Instrument check: the loop really did retry many times.
        assertTrue(
            factory.created >= 10,
            "expected the reconnect loop to have made >= 10 attempts, made ${factory.created}"
        )
        assertEquals(
            factory.created,
            factory.closed,
            "every allocated connection should have been closed"
        )
        assertEquals(0, factory.live, "connections allocated and never closed")
    }

    @Test
    fun droppedKeepAliveDoesNotAccumulateOpenConnections() = runTest {
        // Each attempt connects successfully, then the keep-alive ping fails —
        // the "backend restarted under us" case, where the old client is
        // abandoned in favour of a new one.
        val factory = CountingConnectionFactory(successfulPingsPerConnection = 1)
        ServiceHolder(factory, backgroundScope)

        advanceTimeBy(130_001)
        runCurrent()

        assertTrue(
            factory.created >= 5,
            "expected >= 5 connect/drop cycles, saw ${factory.created}"
        )
        // At most the one connection currently established is still open; every
        // earlier cycle's connection must already have been closed.
        assertTrue(
            factory.live <= 1,
            "expected <= 1 open connection, ${factory.live} of ${factory.created} " +
                "were allocated and never closed"
        )
    }

    @Test
    fun cancellingTheScopeClosesTheLiveConnection() = runTest {
        val factory = CountingConnectionFactory(successfulPingsPerConnection = Int.MAX_VALUE)
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
        ServiceHolder(factory, scope)

        advanceTimeBy(1)
        assertEquals(1, factory.created)
        assertEquals(1, factory.live, "expected one established connection")

        scope.cancel()
        advanceTimeBy(1)
        runCurrent()

        assertEquals(0, factory.live, "cancellation must not strand the open connection")
    }
}

private class CountingConnectionFactory(private val successfulPingsPerConnection: Int) :
    ConnectionFactory {
    var created = 0
        private set
    var closed = 0
        private set

    val live: Int get() = created - closed

    override suspend fun connect(): ServiceConnection {
        created++
        return CountingConnection(successfulPingsPerConnection) { closed++ }
    }
}

private class CountingConnection(successfulPings: Int, private val onClose: () -> Unit) :
    ServiceConnection {
    private var isClosed = false

    override val stub: Konstructor = FlakyKonstructor(successfulPings)

    override suspend fun close() {
        check(!isClosed) { "connection closed twice" }
        isClosed = true
        onClose()
    }
}

private class FlakyKonstructor(private var remainingSuccessfulPings: Int) : Konstructor {
    override suspend fun ping(u: Unit) {
        if (remainingSuccessfulPings <= 0) {
            throw IllegalStateException("connection refused")
        }
        if (remainingSuccessfulPings != Int.MAX_VALUE) {
            remainingSuccessfulPings--
        }
    }

    override suspend fun list(u: Unit): List<Space> = error("unused")
    override suspend fun get(id: String): Workspace = error("unused")
    override suspend fun konstruction(id: Konstruction): KonstructionService = error("unused")
    override suspend fun create(newItem: Space): Space = error("unused")
    override suspend fun delete(item: Space) = error("unused")
    override suspend fun getGlobalShipper(u: Unit): Shipper = error("unused")
}
