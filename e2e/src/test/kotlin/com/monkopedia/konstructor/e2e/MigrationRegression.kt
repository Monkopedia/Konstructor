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
package com.monkopedia.konstructor.e2e

/**
 * Marks a test that codifies a regression introduced by the Kotlin/JS + React →
 * Kotlin/WasmJS + Compose Multiplatform migration (see GitHub issue #3).
 *
 * Two kinds of marked tests exist:
 *  - **Passing** tests lock in a regression that has already been fixed (e.g.
 *    Z-up camera, selection persistence, render-reload cache-busting) so it
 *    cannot silently regress again.
 *  - **`@org.junit.Ignore`-d** tests document a regression that is still open.
 *    The body encodes the *expected* (pre-migration) behavior; the `@Ignore`
 *    reason carries the issue link so CI stays green while the gap is visible.
 *
 * [finding] is the issue #3 finding number (1..9) this test maps to.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class MigrationRegression(val finding: Int, val description: String = "")

/** Canonical issue link reused in `@Ignore` reasons for traceability. */
const val MIGRATION_ISSUE = "https://github.com/Monkopedia/konstructor/issues/3"
