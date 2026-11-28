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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ReconnectBackoffTest {

    @Test
    fun delaysDoubleUpToTheCap() {
        val backoff = ReconnectBackoff()

        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            List(7) { backoff.nextDelay() }
        )
    }

    @Test
    fun resetStartsOverFromTheInitialDelay() {
        val backoff = ReconnectBackoff()
        repeat(4) { backoff.nextDelay() }

        backoff.reset()

        assertEquals(1_000L, backoff.nextDelay())
    }

    @Test
    fun visibleTabWaitsTheBackoffDelay() = runTest {
        val backoff = ReconnectBackoff()
        val visible = MutableStateFlow(true)

        backoff.awaitNextAttempt(visible)
        assertEquals(1_000L, currentTime)
        backoff.awaitNextAttempt(visible)
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun hiddenTabPausesUntilVisibleThenRetriesImmediatelyWithResetBackoff() = runTest {
        val backoff = ReconnectBackoff()
        repeat(5) { backoff.nextDelay() } // next delay would be 30s
        val visible = MutableStateFlow(false)

        val done = launchAwait(backoff, visible)
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(false, done.value, "must not retry while the tab is hidden")

        visible.value = true
        runCurrent()
        assertEquals(true, done.value, "must retry as soon as the tab is visible")
        assertEquals(10 * 60_000L, currentTime, "no extra delay after becoming visible")
        assertEquals(1_000L, backoff.nextDelay(), "backoff resets on becoming visible")
    }

    @Test
    fun tabHiddenMidWaitStopsTheTimer() = runTest {
        val backoff = ReconnectBackoff()
        val visible = MutableStateFlow(true)

        val done = launchAwait(backoff, visible)
        advanceTimeBy(500)
        visible.value = false
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(false, done.value, "the 1s delay must not fire while hidden")

        visible.value = true
        runCurrent()
        assertEquals(true, done.value)
    }

    private fun TestScope.launchAwait(
        backoff: ReconnectBackoff,
        visible: MutableStateFlow<Boolean>
    ): MutableStateFlow<Boolean> {
        val done = MutableStateFlow(false)
        launch {
            backoff.awaitNextAttempt(visible)
            done.value = true
        }
        return done
    }
}
