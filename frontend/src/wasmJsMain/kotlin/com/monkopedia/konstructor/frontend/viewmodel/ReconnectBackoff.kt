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

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The reconnect cadence: exponential backoff from [initialDelayMs], doubling up
 * to [maxDelayMs], with no jitter (there is one tab per backend, not a fleet of
 * clients to de-synchronise). [reset] puts it back at [initialDelayMs]; call it
 * once a connection is established.
 */
internal class ReconnectBackoff(
    private val initialDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L
) {
    private var nextDelayMs = initialDelayMs

    /** The delay before the next attempt; each call doubles the following one, up to the cap. */
    fun nextDelay(): Long {
        val delayMs = nextDelayMs
        nextDelayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
        return delayMs
    }

    fun reset() {
        nextDelayMs = initialDelayMs
    }

    /**
     * Suspends until the next attempt is due. Normally that is [nextDelay], but
     * retrying is paused while the tab is hidden: if [tabVisible] is or becomes
     * false during the wait, this waits for the tab to be visible again and then
     * returns immediately with the schedule [reset], so coming back to the tab
     * reconnects without sitting out a long backoff.
     */
    suspend fun awaitNextAttempt(tabVisible: StateFlow<Boolean>) {
        val wentHidden = withTimeoutOrNull(nextDelay()) { tabVisible.first { !it } } != null
        if (wentHidden) {
            tabVisible.first { it }
            reset()
        }
    }
}
