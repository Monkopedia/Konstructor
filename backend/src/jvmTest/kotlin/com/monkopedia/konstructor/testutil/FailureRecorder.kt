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
package com.monkopedia.konstructor.testutil

import com.monkopedia.hauler.Garage
import com.monkopedia.hauler.Level
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a block let escape: exceptions that reached the JVM's default uncaught handler, and
 * the messages logged at ERROR through hauler while it ran.
 */
class FailureRecorder {
    val uncaught: MutableList<Throwable> = CopyOnWriteArrayList()
    val errorLogs: MutableList<String> = CopyOnWriteArrayList()

    /** Waits for an ERROR log containing [fragment]; false if none arrives within [timeout]. */
    suspend fun awaitErrorLog(fragment: String, timeout: Duration = 10.seconds): Boolean =
        withTimeoutOrNull(timeout) {
            while (errorLogs.none { fragment in it }) delay(20)
            true
        } ?: false
}

/**
 * Runs [block] with a recording default uncaught handler (restoring the previous one) and a
 * subscription to every ERROR hauler logs, then DRAINS anything `kotlinx-coroutines-test`
 * stashed on the way past.
 *
 * The drain is not tidiness — without it a deliberate leak reproduces the very bug these
 * tests guard. `ExceptionCollector.handleException` stashes an exception into
 * `unprocessedExceptions` whenever no `runTest` is active (and still forwards it to the
 * thread handler, which is how the recorder sees it), and that collector latches on for the
 * rest of the fork after the first `runTest`. The next `TestScope.enter()` drains the stash
 * and throws `UncaughtExceptionsBeforeTest` — so a leak recorded here would fail an unrelated
 * test later in the run, which is exactly konstructor#101's signature. Entering and
 * discarding one `runTest` consumes the stash at a known point instead.
 */
suspend fun recordFailures(block: suspend FailureRecorder.() -> Unit): FailureRecorder {
    val recorder = FailureRecorder()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, thrown -> recorder.uncaught += thrown }
    try {
        coroutineScope {
            // UNDISPATCHED so the subscription exists before [block] can log anything.
            val logs = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                Garage.deliveries.filter { it.level == Level.ERROR }
                    .collect { recorder.errorLogs += it.message }
            }
            recorder.block()
            logs.cancel()
        }
    } finally {
        Thread.setDefaultUncaughtExceptionHandler(previous)
        runCatching { runTest { } }
    }
    return recorder
}
