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
package com.monkopedia.konstructor.lsp

import com.monkopedia.lsp.Diagnostic
import com.monkopedia.lsp.DiagnosticOptions
import com.monkopedia.lsp.DocumentDiagnosticReport
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.RelatedFullDocumentDiagnosticReport
import com.monkopedia.lsp.RelatedUnchangedDocumentDiagnosticReport
import com.monkopedia.lsp.ServerCapabilities
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest

/**
 * Unit guards for the event-driven pull→push diagnostics model (#43), exercised on the
 * extraction-ready [PullDiagnosticsPublisher] directly (no engine, no transport): the
 * machinery is given fake pull/publish seams and driven through the trigger surface.
 *
 * Covers the four behaviours the maintainer review pins down:
 *  - **Pull-mode detection gating** — only a non-null `diagnosticProvider` is pull mode.
 *  - **`unchanged`-report skip** — an unchanged report must NOT republish.
 *  - **`previousResultId` threading** — the last `resultId` is fed to the next pull.
 *  - **refresh → re-pull** — `onRefresh` re-pulls every open doc.
 *
 * Plus the teardown contract the publisher now relies on (#80): the cold-index retry has no
 * attempt countdown of its own, so cancelling the injected scope must stop it.
 */
class PullDiagnosticsPublisherTest {

    private val uri = "file:///0/0/content.csgs"

    /** The pull deadline under test. Short so the virtual clock reaches it obviously. */
    private val pullDeadline = 30.seconds

    private fun fullReport(resultId: String?, vararg messages: String): DocumentDiagnosticReport =
        RelatedFullDocumentDiagnosticReport(
            kind = "full",
            resultId = resultId,
            items = messages.map { diag(it) }
        )

    private fun unchangedReport(resultId: String): DocumentDiagnosticReport =
        RelatedUnchangedDocumentDiagnosticReport(kind = "unchanged", resultId = resultId)

    private fun diag(message: String): Diagnostic = Diagnostic(
        range = Range(start = Position(0u, 0u), end = Position(0u, 0u)),
        message = message
    )

    /** A pull/publish recorder driving the publisher with scripted reports. */
    private class Recorder {
        val pullCalls = CopyOnWriteArrayList<String?>()
        val publishes = CopyOnWriteArrayList<List<Diagnostic>>()
        val publishUris = CopyOnWriteArrayList<String>()
        var reports: () -> DocumentDiagnosticReport = { error("no report scripted") }

        /**
         * Optional gate: when non-null, [PullDiagnosticsPublisher.pull] suspends on it before
         * returning the scripted report — lets a test hold a pull "in flight" across an
         * onClose to exercise the publish-after-close race guard.
         */
        var pullGate: CompletableDeferred<Unit>? = null
    }

    // --- pull-mode detection gating ---------------------------------------------------

    @Test
    fun `pull mode is detected only when diagnosticProvider is present`() {
        val pullMode = ServerCapabilities(
            diagnosticProvider = DiagnosticOptions(
                interFileDependencies = true,
                workspaceDiagnostics = false
            )
        )
        assertNotNull(
            PullDiagnosticsPublisher.pullProviderOf(pullMode),
            "a non-null diagnosticProvider must be reported as pull mode"
        )
        assertNull(
            PullDiagnosticsPublisher.pullProviderOf(ServerCapabilities()),
            "no diagnosticProvider ⇒ not pull mode (the bridge runs no pull machinery)"
        )
        assertNull(
            PullDiagnosticsPublisher.pullProviderOf(null),
            "absent capabilities ⇒ not pull mode"
        )
    }

    @Test
    fun `provider sub-flags are surfaced for re-pull breadth decisions`() = runTest {
        val rec = Recorder()
        val publisher = newPublisher(
            rec,
            provider = DiagnosticOptions(
                interFileDependencies = true,
                workspaceDiagnostics = true
            )
        )
        assertTrue(publisher.interFileDependencies)
        assertTrue(publisher.workspaceDiagnostics)
    }

    // --- previousResultId threading ---------------------------------------------------

    @Test
    fun `onOpen pulls with no previousResultId and threads the returned resultId`() = runTest {
        val rec = Recorder()
        rec.reports = { fullReport(resultId = "rid-1", "boom") }
        val publisher = newPublisher(rec)

        publisher.onOpen(uri)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf<String?>(null), rec.pullCalls, "first pull carries no previousResultId")
        assertEquals(1, rec.publishes.size, "a full report must publish")

        // A subsequent change-triggered pull must thread the resultId from the first report.
        publisher.onChange(uri)
        testScheduler.advanceUntilIdle()
        assertEquals(
            listOf<String?>(null, "rid-1"),
            rec.pullCalls,
            "the next pull must thread the previous resultId"
        )
    }

    // --- unchanged-report skip --------------------------------------------------------

    @Test
    fun `an unchanged report does not republish but still advances the resultId`() = runTest {
        val rec = Recorder()
        // First pull: full (rid-1, publishes). Second pull: unchanged (rid-2, no publish).
        val scripted = ArrayDeque(
            listOf(
                fullReport(resultId = "rid-1", "boom"),
                unchangedReport(resultId = "rid-2")
            )
        )
        rec.reports = { scripted.removeFirst() }
        val publisher = newPublisher(rec)

        publisher.onOpen(uri)
        testScheduler.advanceUntilIdle()
        assertEquals(1, rec.publishes.size, "full report publishes once")

        publisher.onChange(uri)
        testScheduler.advanceUntilIdle()
        assertEquals(
            1,
            rec.publishes.size,
            "an unchanged report must NOT add a redundant publish"
        )
        // And the third pull threads rid-2 (the unchanged report's resultId).
        rec.reports = { unchangedReport(resultId = "rid-3") }
        publisher.onChange(uri)
        testScheduler.advanceUntilIdle()
        assertEquals(
            listOf<String?>(null, "rid-1", "rid-2"),
            rec.pullCalls,
            "the unchanged report's resultId must thread forward"
        )
    }

    // --- refresh → re-pull ------------------------------------------------------------

    @Test
    fun `onRefresh re-pulls every open doc`() = runTest {
        val rec = Recorder()
        rec.reports = { fullReport(resultId = "rid", "boom") }
        val publisher = newPublisher(rec)

        publisher.onOpen(uri)
        testScheduler.advanceUntilIdle()
        val afterOpen = rec.pullCalls.size

        publisher.onRefresh()
        testScheduler.advanceUntilIdle()
        assertEquals(
            afterOpen + 1,
            rec.pullCalls.size,
            "refresh must re-pull the open doc"
        )
        assertTrue(rec.publishes.size >= 2, "refresh republished the full report")
    }

    @Test
    fun `onClose stops the doc being re-pulled on refresh`() = runTest {
        val rec = Recorder()
        rec.reports = { fullReport(resultId = "rid", "boom") }
        val publisher = newPublisher(rec)

        publisher.onOpen(uri)
        testScheduler.advanceUntilIdle()
        val afterOpen = rec.pullCalls.size

        publisher.onClose(uri)
        publisher.onRefresh()
        testScheduler.advanceUntilIdle()
        assertEquals(
            afterOpen,
            rec.pullCalls.size,
            "a closed doc must not be re-pulled on refresh"
        )
    }

    // --- close-time correctness (the 3 maintainer fixes, #40) -------------------------

    @Test
    fun `onClose clears state and emits an empty publish so squiggles do not linger`() = runTest {
        val rec = Recorder()
        rec.reports = { fullReport(resultId = "rid", "boom") }
        val publisher = newPublisher(rec)

        publisher.onOpen(uri)
        testScheduler.advanceUntilIdle()
        val publishesBeforeClose = rec.publishes.size

        publisher.onClose(uri)
        testScheduler.advanceUntilIdle()

        // A clearing publish for the closed doc must have been emitted...
        assertEquals(
            publishesBeforeClose + 1,
            rec.publishes.size,
            "onClose must emit one clearing publish"
        )
        assertEquals(uri, rec.publishUris.last(), "the clearing publish targets the closed uri")
        assertTrue(
            rec.publishes.last().isEmpty(),
            "the clearing publish must carry an EMPTY diagnostics list"
        )

        // ...and the doc state must be gone: a later change must pull with NO previousResultId
        // (the resultId was cleared), proving onClose forgot the doc.
        rec.reports = { fullReport(resultId = "rid2", "again") }
        publisher.onChange(uri)
        testScheduler.advanceUntilIdle()
        assertEquals(
            null,
            rec.pullCalls.last(),
            "after close the resultId is cleared, so the next pull threads no previousResultId"
        )
    }

    @Test
    fun `an in-flight pull that finishes after close does not publish`() = runTest {
        val rec = Recorder()
        val gate = CompletableDeferred<Unit>()
        rec.pullGate = gate
        rec.reports = { fullReport(resultId = "rid", "boom") }
        val publisher = newPublisher(rec)

        // Start a change-triggered pull and let it reach the (gated) pull seam, then hold it.
        // Stepped rather than advanceUntilIdle(): a pull now has a deadline, and running the
        // clock to idle would fire it and leave this test asserting nothing.
        publisher.onChange(uri)
        testScheduler.advanceTimeBy(1.seconds)
        assertEquals(1, rec.pullCalls.size, "the change pull reached the pull seam")
        // Nothing published yet (pull is suspended on the gate); also no clearing publish yet.
        val publishesWhileInFlight = rec.publishes.size

        // The doc closes while the pull is still in flight. This drops it from openDocs and
        // emits the clear-on-close publish.
        publisher.onClose(uri)
        testScheduler.advanceTimeBy(1.seconds)
        val afterClose = rec.publishes.size

        // Now let the in-flight pull complete: its (now-stale) full report must NOT publish,
        // because the doc is no longer open.
        gate.complete(Unit)
        testScheduler.advanceTimeBy(1.seconds)

        assertEquals(
            afterClose,
            rec.publishes.size,
            "a pull that completes AFTER close must not publish stale diagnostics for the doc"
        )
        // The only diagnostics-bearing publish must have been the clearing (empty) one.
        assertTrue(
            rec.publishes.all { it.isEmpty() },
            "no non-empty publish should reach a closed doc; got ${rec.publishes}"
        )
        // Sanity: we did exercise the in-flight path (a publish happened only at/after close).
        assertTrue(publishesWhileInFlight <= afterClose)
    }

    @Test
    fun `onOpen and onClose keyed by the same uri fully release the doc`() = runTest {
        val rec = Recorder()
        rec.reports = { fullReport(resultId = "rid", "boom") }
        val publisher = newPublisher(rec)

        // Open and close with the SAME uri (the contract: all lifecycle callbacks key by one
        // uri space). A subsequent refresh must NOT re-pull — the doc left openDocs.
        publisher.onOpen(uri)
        testScheduler.advanceUntilIdle()
        val afterOpen = rec.pullCalls.size

        publisher.onClose(uri)
        testScheduler.advanceUntilIdle()

        publisher.onRefresh()
        testScheduler.advanceUntilIdle()
        assertEquals(
            afterOpen,
            rec.pullCalls.size,
            "a consistently-keyed close removes the doc; refresh must not re-pull it (no leak)"
        )
    }

    // --- cancellation is what bounds the cold-index retry (#80) -----------------------

    @Test
    fun `the cold-index retry runs until its scope is cancelled`() = runTest {
        val rec = Recorder()
        rec.reports = { error("engine is down") }
        // The publisher is launched in the ENGINE CONNECTION's scope (#80), which the bridge
        // cancels when that connection dies or is replaced.
        val connectionScope = CoroutineScope(coroutineContext + Job())
        val publisher = connectionScope.newPublisher(rec)

        publisher.onOpen(uri)
        testScheduler.advanceTimeBy(30.seconds)
        val whileConnected = rec.pullCalls.size
        assertTrue(
            whileConnected > 1,
            "a failing first pull must keep retrying while the connection lives; got $whileConnected"
        )

        // Cancelling the connection's scope is the ONLY thing that bounds the retry — there is no
        // attempt countdown any more.
        connectionScope.cancel()
        testScheduler.advanceTimeBy(5.minutes)
        assertEquals(
            whileConnected,
            rec.pullCalls.size,
            "a cancelled scope must stop the retry dead, not keep polling a dead engine"
        )
    }

    // --- a pull is bounded, not merely re-tried (#109) --------------------------------

    @Test
    fun `a pull the engine never answers is bounded and re-tried, not parked`() = runTest {
        val rec = Recorder()
        // The engine takes the pull and never answers — #109's shape at the seam a user
        // notices first, and the one with no self-heal: reconnect only ever fires from a
        // request-shaped call, and a user who is only typing makes none.
        rec.pullGate = CompletableDeferred()
        rec.reports = { fullReport(resultId = "rid", "boom") }
        // Same shape as the cancellation guard below: the retry loop is unbounded by design, so
        // it runs in the engine connection's scope and the test ends it the way the bridge does.
        val connectionScope = CoroutineScope(coroutineContext + Job())
        val publisher = connectionScope.newPublisher(rec)

        publisher.onOpen(uri)
        // Long enough for several deadline+cadence cycles. [coldIndexRetry] cannot rescue
        // this on its own: it is the gap BETWEEN completed pulls, so an unbounded pull never
        // reaches it and pullCalls stays stuck at 1 for the life of the session.
        testScheduler.advanceTimeBy((pullDeadline + 10.seconds) * 3)
        val attempts = rec.pullCalls.size
        connectionScope.cancel()

        assertTrue(
            attempts > 1,
            "a pull that never answers must hit its deadline and be re-tried; the loop only " +
                "made $attempts attempt(s), i.e. it is parked on the first one"
        )
    }

    private fun CoroutineScope.newPublisher(
        rec: Recorder,
        pullTimeout: Duration = pullDeadline,
        provider: com.monkopedia.lsp.ServerCapabilitiesDiagnosticProvider = DiagnosticOptions(
            interFileDependencies = false,
            workspaceDiagnostics = false
        )
    ): PullDiagnosticsPublisher = PullDiagnosticsPublisher(
        scope = this,
        pullTimeout = pullTimeout,
        diagnosticProvider = provider,
        pull = { _, previousResultId ->
            rec.pullCalls.add(previousResultId)
            rec.pullGate?.await()
            rec.reports()
        },
        publish = { uri, diagnostics ->
            rec.publishUris.add(uri)
            rec.publishes.add(diagnostics)
        },
        awaitReady = { }
    )
}
