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
package com.monkopedia.konstructor.lib

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WriteAtomicallyTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("write-atomically-").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Writes half of [content], then dies, standing in for a kill mid-write. */
    private fun diesHalfway(content: String): (OutputStream) -> Unit = { out ->
        out.write(content.toByteArray(), 0, content.length / 2)
        out.flush()
        throw IOException("killed mid-write")
    }

    @Test
    fun aWriteThatDiesLeavesNoCacheEntry() {
        // #166: the cache is keyed only by exists(), so a truncated <hash>.stl from an
        // in-place write was served as the cached geometry by every later build.
        val target = File(dir, "abc.stl")
        assertFailsWith<IOException> { target.writeAtomically(diesHalfway(SOLID)) }
        assertFalse(target.exists(), "a torn write must not leave a cache entry")
        assertEquals(emptyList(), dir.list()!!.toList(), "nor a stray temp file")
    }

    @Test
    fun aWriteThatDiesLeavesThePreviousContent() {
        val target = File(dir, "abc.stl")
        target.writeAtomically { it.write(SOLID.toByteArray()) }
        assertFailsWith<IOException> { target.writeAtomically(diesHalfway("other $SOLID")) }
        assertEquals(SOLID, target.readText())
    }

    @Test
    fun concurrentWritersOfOneHashAllSucceedWithACompleteFile() {
        // Two renders of the same script store the same hash. Each needs its own temp
        // file, or one writer's move would take the other's half-written bytes.
        val target = File(dir, "abc.stl")
        val payloads = (0 until WRITERS).map { "solid w$it\n" + "facet\n".repeat(2000) }
        val pool = Executors.newFixedThreadPool(WRITERS)
        val failures = try {
            payloads.map { p ->
                pool.submit<Throwable?> {
                    runCatching {
                        repeat(WRITES_EACH) { target.writeAtomically { it.write(p.toByteArray()) } }
                    }.exceptionOrNull()
                }
            }.mapNotNull { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        assertEquals(emptyList(), failures)
        assertTrue(target.readText() in payloads, "the cache entry must be one complete write")
        assertEquals(listOf("abc.stl"), dir.list()!!.toList(), "no temp file may outlive its move")
    }

    private companion object {
        const val SOLID = "solid cube\nfacet normal 0 0 1\nendsolid cube\n"
        const val WRITERS = 8
        const val WRITES_EACH = 50
    }
}
