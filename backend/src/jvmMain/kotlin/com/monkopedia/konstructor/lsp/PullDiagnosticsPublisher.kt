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

import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.lsp.Diagnostic
import com.monkopedia.lsp.DiagnosticOptions
import com.monkopedia.lsp.DiagnosticRegistrationOptions
import com.monkopedia.lsp.DocumentDiagnosticParams
import com.monkopedia.lsp.DocumentDiagnosticReport
import com.monkopedia.lsp.RelatedFullDocumentDiagnosticReport
import com.monkopedia.lsp.RelatedUnchangedDocumentDiagnosticReport
import com.monkopedia.lsp.ServerCapabilities
import com.monkopedia.lsp.ServerCapabilitiesDiagnosticProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The event-driven **pull→push diagnostics bridge** for LSP #43 (epic #35).
 *
 * kotlin-lsp is a LSP 3.17 **pull-mode** server: it does not proactively push
 * `publishDiagnostics`; it answers PULL `textDocument/diagnostic`, and signals "results
 * may have changed, re-pull" out of band via `workspace/diagnostic/refresh`. kodemirror's
 * editor client, by contrast, is **push-only** (consumes pushed `publishDiagnostics`). So
 * the bridge must turn pulls into pushes — but *event-driven*, not by blind polling
 * through the cold index (the Phase 2 stop-gap this class replaces).
 *
 * This unit is deliberately factored as a **cohesive, transport-agnostic publisher** so it
 * can later be lifted into `lsp-ksrpc` as a reusable `DiagnosticBridge`/
 * `PullDiagnosticsPublisher` (the lsp-kotlin maintainer offered to review it for that). It
 * is given only:
 *
 * **Module-extraction decision (epic #35 Phase 5 / #40): NOT YET.** Per the lsp-kotlin
 * maintainer, the LSP proxy/translation layer + this `PullDiagnosticsPublisher` are
 * extraction-ready (the seams below are transport- AND translation-agnostic; all csgs↔kt
 * translation lives in the bridge seams) but should **bake in konstructor first**. So
 * nothing is extracted here: the proxy/translation + publisher stay in the backend until the
 * design is proven live. Extraction into a `csgs-lsp` module / an lsp-ksrpc `DiagnosticBridge`
 * is deferred — the lsp-kotlin maintainer will lift `PullDiagnosticsPublisher` when ready
 * (extraction-time affordances captured on #40: optional `identifier` keying resultId by
 * (uri, identifier?); `version` through the publish seam; `awaitReady` as a generic
 * `suspend () -> Unit`; the teardown contract — the publisher relies on its injected [scope]
 * being cancelled, which `BridgeLanguageServer.close()` does).
 *  - [pull]: how to PULL a [DocumentDiagnosticReport] for a doc (the subprocess stub's
 *    `textDocumentDiagnostic`, with `previousResultId`),
 *  - [publish]: how to PUSH a doc's diagnostics up to the target client (the bridge wires
 *    this to translate ranges to csgs space + rewrite the URI, then call
 *    `textDocumentPublishDiagnostics`),
 *  - [awaitReady]: a gate suspended on until the target client is ready to receive (the
 *    `initialized` handshake), and
 *  - timing knobs ([debounce], cold-index [backoff]).
 *
 * It tracks per-doc [previousResultId][DocumentDiagnosticParams.previousResultId] (see
 * [resultIds]); on each pull it threads the last id, and when the server answers an
 * **`unchanged`** report ([RelatedUnchangedDocumentDiagnosticReport], `kind == "unchanged"`)
 * it does **not** republish — only a **`full`** report ([RelatedFullDocumentDiagnosticReport],
 * `kind == "full"`) emits a `publishDiagnostics`. This kills redundant upward pushes.
 *
 * Triggers:
 *  - [onOpen] — pull immediately (the doc just opened); bounded cold-index backoff applies
 *    to this FIRST pull only, since the index may not be warm yet (a pull can transiently
 *    fail or come back empty before analysis settles).
 *  - [onChange] — debounced pull for that doc (coalesces a burst of edits/saves).
 *  - [onRefresh] — re-pull **all** open docs; this is the engine's
 *    `workspace/diagnostic/refresh` (the cold-index-warmed case), and is the steady-state
 *    event that makes this event-driven rather than a poll loop.
 *
 * Pull-mode is **gated** by the caller on [ServerCapabilities.diagnosticProvider] being
 * non-null (there is no push capability flag, so a non-null diagnosticProvider is the
 * authoritative pull-mode signal — see [pullProviderOf]); this class is only constructed
 * for pull-mode servers. [interFileDependencies] is surfaced informational (editing one
 * doc can change another's diagnostics → governs re-pull breadth), and a refresh already
 * re-pulls every open doc, which honours it.
 */
internal class PullDiagnosticsPublisher(
    private val scope: CoroutineScope,
    /** Authoritative pull-mode capability (non-null ⇒ pull mode). Surfaced for callers. */
    val diagnosticProvider: ServerCapabilitiesDiagnosticProvider,
    /**
     * Pull the diagnostic report for `uri`, threading the last-seen `previousResultId`
     * (null on the first pull). Returns the raw report; this class interprets full vs
     * unchanged.
     */
    private val pull: suspend (uri: String, previousResultId: String?) -> DocumentDiagnosticReport,
    /** Push a `full` report's diagnostics up to the target client for a doc. */
    private val publish: suspend (uri: String, diagnostics: List<Diagnostic>) -> Unit,
    /** Suspends until the target client has signalled it is ready (the `initialized` gate). */
    private val awaitReady: suspend () -> Unit,
    private val debounce: Duration = DEFAULT_DEBOUNCE,
    private val backoff: ColdIndexBackoff = ColdIndexBackoff(),
    /**
     * Whether [onClose] emits a clearing `publish(uri, emptyList())` so the editor drops the
     * last squiggles when a doc closes (kodemirror does not always clear on its own didClose).
     * Defaulted on; a knob for the eventual lsp-ksrpc extraction.
     */
    private val clearOnClose: Boolean = true
) {
    private val hauler = hauler("PullDiagnosticsPublisher")

    /**
     * Whether editing one doc can change another doc's diagnostics. Informational here
     * (an [onRefresh] already re-pulls every open doc); surfaced for callers/extraction.
     */
    val interFileDependencies: Boolean = when (diagnosticProvider) {
        is DiagnosticOptions -> diagnosticProvider.interFileDependencies
        is DiagnosticRegistrationOptions -> diagnosticProvider.interFileDependencies
    }

    /** Whether the server also answers whole-workspace `workspace/diagnostic`. Informational. */
    val workspaceDiagnostics: Boolean = when (diagnosticProvider) {
        is DiagnosticOptions -> diagnosticProvider.workspaceDiagnostics
        is DiagnosticRegistrationOptions -> diagnosticProvider.workspaceDiagnostics
    }

    /** The set of docs currently open in the engine, re-pulled on a refresh. */
    private val openDocs: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Per-doc last-seen result id, threaded as `previousResultId` on the next pull. */
    private val resultIds = ConcurrentHashMap<String, String>()

    /** Per-doc in-flight debounced-change job, replaced on each [onChange]. */
    private val changeJobs = ConcurrentHashMap<String, Job>()

    /** Serializes pulls for a single doc so result-id threading is race-free. */
    private val pullLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(uri: String): Mutex = pullLocks.getOrPut(uri) { Mutex() }

    /**
     * Register `uri` as open and pull its diagnostics now, with bounded cold-index backoff
     * (this first pull may race the index warming). Replaces the Phase 2 unconditional
     * retry loop: once the engine sends a refresh we're event-driven, so backoff applies
     * ONLY to this initial pull.
     */
    fun onOpen(uri: String) {
        openDocs.add(uri)
        scope.launch {
            awaitReady()
            pullWithColdIndexBackoff(uri)
        }
    }

    /**
     * A doc changed (didChange/didSave). Debounce a re-pull for it, coalescing a burst of
     * edits into a single pull once the user pauses.
     */
    fun onChange(uri: String) {
        openDocs.add(uri)
        changeJobs.put(
            uri,
            scope.launch {
                awaitReady()
                delay(debounce)
                pullOnce(uri)
            }
        )?.cancel()
    }

    /**
     * A doc closed: forget its result id / open state / pending change pull. The doc is
     * removed from [openDocs] FIRST so any in-flight pull (from [onOpen]/[onChange]) that has
     * not yet published sees `uri !in openDocs` in [pullOnce] and skips the publish — no stale
     * diagnostics reappear after close (publish-after-close race). When [clearOnClose] we then
     * emit a clearing `publish(uri, emptyList())` so the editor drops the last squiggles
     * (kodemirror does not always clear on its own didClose).
     *
     * Keyed by the SAME uri space as [onOpen]/[onChange] (the frontend csgs uri): the seams
     * translate at the boundary, the lifecycle callbacks all key consistently — otherwise a
     * mismatched key would leave the doc in [openDocs]/[resultIds] forever (re-pulled on every
     * refresh after close, a real leak).
     */
    fun onClose(uri: String) {
        openDocs.remove(uri)
        resultIds.remove(uri)
        changeJobs.remove(uri)?.cancel()
        if (clearOnClose) {
            scope.launch {
                runCatching { publish(uri, emptyList()) }
                    .onFailure { hauler.error("clear-on-close publish failed for $uri", it) }
            }
        }
    }

    /**
     * The engine signalled `workspace/diagnostic/refresh` ("results may have changed,
     * re-pull"). Re-pull EVERY open doc — this is the cold-index-warmed event and the
     * steady-state driver of the event-driven model. Honours [interFileDependencies] by
     * re-pulling breadth (all open docs), not just one.
     */
    fun onRefresh() {
        scope.launch {
            awaitReady()
            for (uri in openDocs.toList()) {
                pullOnce(uri)
            }
        }
    }

    /**
     * Pull once for `uri`, threading `previousResultId` and republishing ONLY on a `full`
     * report (an `unchanged` report means "no change since `previousResultId`" → skip the
     * redundant push). Result-id-threaded under a per-doc lock so concurrent triggers don't
     * interleave a stale id.
     */
    private suspend fun pullOnce(uri: String): PullOutcome = lockFor(uri).withLock {
        val previousResultId = resultIds[uri]
        val report = runCatching { pull(uri, previousResultId) }
            .onFailure { hauler.error("pull diagnostics failed for $uri", it) }
            .getOrElse { return@withLock PullOutcome.FAILED }
        when (report) {
            is RelatedFullDocumentDiagnosticReport -> {
                report.resultId?.let { resultIds[uri] = it }
                // Publish-after-close race: this pull may have been launched by onOpen/onChange
                // before the doc closed. If onClose has since removed it from openDocs, drop the
                // publish so stale diagnostics never reappear for a now-closed doc.
                if (uri !in openDocs) return@withLock PullOutcome.SKIPPED_CLOSED
                runCatching { publish(uri, report.items) }
                    .onFailure { hauler.error("publish diagnostics failed for $uri", it) }
                PullOutcome.PUBLISHED
            }

            is RelatedUnchangedDocumentDiagnosticReport -> {
                // Unchanged since previousResultId — refresh the id but DON'T republish.
                resultIds[uri] = report.resultId
                PullOutcome.UNCHANGED
            }
        }
    }

    /**
     * The first pull after [onOpen]: retry with bounded backoff until we either publish or
     * exhaust attempts. The cold index can take ~120s to settle, so a pull may transiently
     * fail or come back empty before analysis lands; once it publishes (or the doc closes)
     * we stop and rely on event-driven [onRefresh]/[onChange].
     */
    private suspend fun pullWithColdIndexBackoff(uri: String) {
        var attempt = 0
        while (attempt < backoff.maxAttempts && uri in openDocs) {
            val outcome = pullOnce(uri)
            if (outcome == PullOutcome.PUBLISHED) return
            attempt++
            delay(backoff.interval)
        }
    }

    private enum class PullOutcome { PUBLISHED, UNCHANGED, FAILED, SKIPPED_CLOSED }

    /**
     * Bounded backoff for the FIRST pull only (before the index warms). Not a steady-state
     * poll: after the first publish we go event-driven (refresh/change). Defaults are
     * generous enough to cover a cold index (~120s) without an unbounded loop.
     */
    data class ColdIndexBackoff(
        val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        val interval: Duration = DEFAULT_INTERVAL
    ) {
        private companion object {
            private const val DEFAULT_MAX_ATTEMPTS = 40
            private val DEFAULT_INTERVAL = 4.seconds
        }
    }

    companion object {
        private val DEFAULT_DEBOUNCE = 300.milliseconds

        /**
         * The authoritative pull-mode signal: the initialize result's
         * [ServerCapabilities.diagnosticProvider]. Non-null ⇒ pull mode (there is no push
         * capability flag). Returns null when the server is not pull-mode (e.g. an
         * inert/empty capabilities response), in which case the caller skips the pull
         * machinery entirely.
         */
        fun pullProviderOf(
            capabilities: ServerCapabilities?
        ): ServerCapabilitiesDiagnosticProvider? = capabilities?.diagnosticProvider
    }
}
