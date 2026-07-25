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
package com.monkopedia.konstructor.logging

import com.monkopedia.hauler.Box
import com.monkopedia.hauler.DeliveryService
import com.monkopedia.hauler.DropBox
import com.monkopedia.hauler.Level
import com.monkopedia.hauler.LoadingDock
import com.monkopedia.hauler.Shipper
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ScopedShipperTest {

    /**
     * Regression test for the self-recursive [ScopedShipper.requestPickup] (called itself instead
     * of the wrapped [Shipper], which produced a [StackOverflowError]). Asserts the call delegates
     * to the wrapped shipper and returns a usable [DropBox].
     */
    @Test
    fun requestPickupDelegatesToWrappedShipper() = runTest {
        var pickupRequests = 0
        val logged = mutableListOf<Box>()
        val sentinel = object : DropBox {
            override suspend fun log(logEvent: Box) {
                logged += logEvent
            }

            override suspend fun close() = Unit
        }
        val fakeShipper = object : Shipper {
            override suspend fun requestPickup(u: Unit): DropBox {
                pickupRequests++
                return sentinel
            }

            override suspend fun requestDockPickup(u: Unit): LoadingDock =
                throw UnsupportedOperationException()

            override suspend fun deliveries(u: Unit): DeliveryService =
                throw UnsupportedOperationException()
        }

        val scoped = ScopedShipper(tagPrefix = "test", name = "scope", shipper = fakeShipper)

        // Before the fix this recursed into the same override and blew the stack; now it must
        // delegate to the wrapped shipper exactly once and return a usable DropBox.
        val dropBox = scoped.requestPickup()

        assertTrue(pickupRequests == 1, "requestPickup should delegate to the wrapped shipper once")

        // The returned DropBox forwards logs to the delegate (scoping the thread name).
        val box = Box(
            level = Level.INFO,
            loggerName = "logger",
            message = "hello",
            timestamp = 0L,
            threadName = "worker",
        )
        dropBox.log(box)
        assertTrue(logged.size == 1, "log should forward to the delegate DropBox")
        assertTrue(
            logged.single().threadName == "test.scope.worker",
            "delegate should receive the scoped thread name",
        )
    }
}
