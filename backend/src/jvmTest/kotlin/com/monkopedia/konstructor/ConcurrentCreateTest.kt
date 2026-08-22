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

import com.monkopedia.konstructor.common.Space
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.junit.After
import org.junit.Before

/**
 * Regression test for konstructor#102: concurrent creates must not mint the same id.
 *
 * The defect was SILENT. `create()` picked the lowest free id, then wrote — and between
 * those two steps another caller could pick the same number. The measured result was eight
 * concurrent creates returning **zero errors** and leaving **five records** on disk: three
 * callers held an id that resolved to someone else's object, and nothing anywhere reported
 * a problem.
 *
 * That is why this test races real callers instead of asserting on a lock or a flag. A
 * single-caller test passes against the broken code; only two callers colliding can tell
 * the difference, and the assertion has to be about the RESULT SET — every returned id
 * distinct, and one record on disk per success — because the failure mode produces no
 * exception to catch.
 */
class ConcurrentCreateTest {

    private lateinit var env: TestEnv

    private class TestEnv {
        val tempDir: File = kotlin.io.path.createTempDirectory("concurrent-create-").toFile()
        val config = Config(tempDir)
        fun close() = tempDir.deleteRecursively()
    }

    @Before
    fun setUp() {
        env = TestEnv()
    }

    @After
    fun tearDown() {
        env.close()
    }

    @Test
    fun concurrentCreatesAllGetDistinctIds() = runBlocking {
        repeat(ROUNDS) { round ->
            val dir = File(env.tempDir, "round-$round").also { it.mkdirs() }
            val gate = CompletableDeferred<Unit>()

            val results = withContext(Dispatchers.IO) {
                (0 until RACERS).map { i ->
                    async {
                        gate.await() // release everyone at once, so they actually collide
                        runCatching {
                            createWithClaimedId(dir, "", env.config.json) { id ->
                                Space(id = id, name = "racer-$i")
                            }
                        }
                    }
                }.also { gate.complete(Unit) }.awaitAll()
            }

            val claimed = results.mapNotNull { it.getOrNull() }
            val failures = results.mapNotNull { it.exceptionOrNull() }

            assertTrue(
                failures.isEmpty(),
                "round $round: auto-allocated ids must not fail on a lost race, they retry. " +
                    "Got: ${failures.map { it::class.simpleName to it.message }}"
            )
            assertEquals(
                RACERS,
                claimed.toSet().size,
                "round $round: $RACERS concurrent creates returned ${claimed.toSet().size} " +
                    "DISTINCT ids ($claimed) — duplicates mean two callers hold the same id " +
                    "and one silently overwrote the other."
            )
            assertEquals(
                RACERS,
                dir.usedIds().size,
                "round $round: $RACERS successful creates must leave $RACERS records on " +
                    "disk, found ${dir.usedIds().size}. Fewer means silent data loss."
            )
        }
    }

    @Test
    fun anExplicitlyRequestedIdStillFailsWhenTaken() = runBlocking {
        val dir = File(env.tempDir, "explicit").also { it.mkdirs() }
        createWithClaimedId(dir, "chosen", env.config.json) { id -> Space(id = id, name = "first") }

        // A caller-supplied id that is taken is a genuine error — retrying would hand them
        // a DIFFERENT object than the one they asked for. Only auto-allocated ids retry.
        val failure = assertFailsWith<IllegalArgumentException> {
            createWithClaimedId(dir, "chosen", env.config.json) { id ->
                Space(id = id, name = "second")
            }
        }
        assertEquals("chosen has been used already", failure.message)
        assertEquals(1, dir.usedIds().size, "the loser must not have overwritten the winner")
    }

    @Test
    fun anIdWhoseDirectoryExistsWithoutInfoIsNotReoffered() = runBlocking {
        // PathController.get() creates directories eagerly on a read path, leaving a dir
        // with no info.json. Deriving free ids from decoded info skipped those, so the id
        // was offered forever and rejected forever. usedIds() reads directory names.
        val dir = File(env.tempDir, "eager").also { it.mkdirs() }
        File(dir, "0").mkdirs()

        val claimed = createWithClaimedId(dir, "", env.config.json) { id ->
            Space(id = id, name = "next")
        }
        assertEquals("1", claimed, "id 0 is taken by an info-less directory; 1 is the next free")
    }

    @Test
    fun listingNeverSeesAHalfWrittenInfoFile() = runBlocking {
        // The second symptom in the issue: `outputStream()` truncates on open, so an
        // in-place rewrite is briefly a PREFIX of valid JSON — and listInfo decodes every
        // entry, so one torn file failed the WHOLE listing. Measured before the fix at
        // 1.5% of listings with realistic payloads and 15% with larger ones.
        //
        // Asserted as ZERO failures over many cycles rather than as a rate: with an atomic
        // move there is no window at all, so any failure here is a real regression.
        val dir = File(env.tempDir, "torn").also { it.mkdirs() }
        createWithClaimedId(dir, "0", env.config.json) { id -> Space(id = id, name = PADDING) }
        val target = File(File(dir, "0"), INFO_JSON)

        var listings = 0
        var failures = 0
        withContext(Dispatchers.IO) {
            val writer = async {
                repeat(REWRITES) { n ->
                    writeInfo(target, env.config.json, Space(id = "0", name = "$PADDING-$n"))
                }
            }
            val reader = async {
                while (writer.isActive) {
                    listings++
                    runCatching { dir.listInfo<Space>(env.config.json) }
                        .onFailure { failures++ }
                }
            }
            writer.await()
            reader.await()
        }
        assertEquals(
            0,
            failures,
            "$failures of $listings concurrent listings saw a torn info.json. An atomic " +
                "move leaves no window, so any failure is a regression to in-place writes."
        )
        assertTrue(listings > 100, "the probe only proves something if it listed a lot: $listings")
    }

    @Test
    fun concurrentWritersToOneFileDoNotBreakEachOther() = runBlocking {
        // A fixed temp name plus an unconditional `finally { delete() }` made writer A's
        // cleanup unlink writer B's in-flight temp: 628 of 1200 writes threw
        // NoSuchFileException on the move. Nothing was corrupted — the target always
        // decoded — but a rename that used to succeed started failing, which is a
        // regression introduced BY the atomicity fix. Reachable via two concurrent
        // setName calls on one workspace.
        val dir = File(env.tempDir, "onefile").also { it.mkdirs() }
        val target = File(dir, INFO_JSON)
        writeInfo(target, env.config.json, Space(id = "0", name = "seed"))

        val failures = java.util.concurrent.atomic.AtomicInteger()
        withContext(Dispatchers.IO) {
            (0 until WRITERS).map { w ->
                async {
                    repeat(WRITES_EACH) { n ->
                        runCatching {
                            writeInfo(target, env.config.json, Space(id = "0", name = "w$w-$n"))
                        }.onFailure { failures.incrementAndGet() }
                    }
                }
            }.awaitAll()
        }
        assertEquals(
            0,
            failures.get(),
            "${failures.get()} of ${WRITERS * WRITES_EACH} concurrent writes to one " +
                "info.json failed — writers are colliding on a shared temp name."
        )
        // And the file must still be readable, i.e. the fix did not trade one defect for
        // another. Decoded directly rather than through listInfo — listInfo scans
        // SUBDIRECTORIES for their info.json, and this probe writes one file straight into
        // `dir`, so listInfo would return 0 here and the assertion would fail against
        // perfectly good code. (It did, first time round.)
        val decoded = target.inputStream().use { env.config.json.decodeFromStream<Space>(it) }
        assertTrue(
            decoded.name.startsWith("w"),
            "the target must still decode to one of the writers' values, got '${decoded.name}'"
        )
        assertEquals(
            emptyList(),
            dir.listFiles()?.filter { it.name.endsWith(".tmp") }?.map { it.name },
            "no temp files may be left behind"
        )
    }

    private companion object {
        const val RACERS = 8
        const val ROUNDS = 25
        const val REWRITES = 400
        const val WRITERS = 4
        const val WRITES_EACH = 300

        /** Big enough that a truncated write is very likely to land mid-token. */
        val PADDING = "n".repeat(2_000)
    }
}
