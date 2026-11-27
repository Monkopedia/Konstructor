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
import com.monkopedia.konstructor.common.TaskResult
import com.monkopedia.konstructor.common.TaskStatus
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.junit.After
import org.junit.Before

class InfoStoreTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: File

    @Before
    fun setUp() {
        root = kotlin.io.path.createTempDirectory("info-store-test-").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun writeSpace(id: String, name: String = "space $id") {
        val dir = File(root, id).also { it.mkdirs() }
        File(dir, INFO_JSON).outputStream().use { output ->
            json.encodeToStream(Space(id = id, name = name), output)
        }
    }

    @Test
    fun listInfoDecodesEveryItemDirectory() = runBlocking {
        writeSpace("0")
        writeSpace("1")
        assertEquals(
            listOf("0", "1"),
            root.listInfo<Space>(json).map { it.id }.sorted()
        )
    }

    @Test
    fun listInfoSkipsLooseFilesAndDirectoriesWithoutInfo() = runBlocking {
        writeSpace("0")
        File(root, "stray.txt").writeText("not a workspace")
        File(root, "half-created").mkdirs()
        assertEquals(listOf("0"), root.listInfo<Space>(json).map { it.id })
    }

    @Test
    fun listInfoSkipsAnUndecodableInfoJsonInsteadOfFailingTheListing() = runBlocking {
        // #140 at the workspace level: KonstructorImpl.list() uses this too, so one bad
        // workspace info.json must not hide every other workspace.
        writeSpace("0")
        File(File(root, "1").also { it.mkdirs() }, INFO_JSON).writeText("""{"id":"1"}""")
        writeSpace("2")
        assertEquals(listOf("0", "2"), root.listInfo<Space>(json).map { it.id }.sorted())
    }

    @Test
    fun listInfoOnMissingDirectoryIsEmpty() = runBlocking {
        assertEquals(emptyList(), File(root, "nope").listInfo<Space>(json))
    }

    @Test
    fun firstFreeIdFillsTheLowestGap() {
        assertEquals("0", firstFreeId(emptyList()))
        assertEquals("1", firstFreeId(listOf("0")))
        assertEquals("2", firstFreeId(listOf("0", "1", "3")))
        // Non-numeric ids never block an allocation.
        assertEquals("0", firstFreeId(listOf("named", "other")))
    }

    @Test
    fun createWithClaimedIdWritesTheItemAndRejectsAReusedExplicitId() {
        val claimed = createWithClaimedId(root, "0", json) { id -> Space(id = id, name = "first") }
        assertEquals("0", claimed)
        val written = File(File(root, "0"), INFO_JSON).inputStream().use {
            json.decodeFromStream<Space>(it)
        }
        assertEquals("first", written.name)

        val failure = assertFailsWith<IllegalArgumentException> {
            createWithClaimedId(root, "0", json) { id -> Space(id = id, name = "second") }
        }
        assertEquals("0 has been used already", failure.message)
    }

    @Test
    fun anEmptyRequestedIdAllocatesTheLowestFree() {
        assertEquals("0", createWithClaimedId(root, "", json) { id -> Space(id, "a") })
        assertEquals("1", createWithClaimedId(root, "", json) { id -> Space(id, "b") })
    }

    @Test
    fun usedIdsCountsDirectoriesEvenWithoutInfoJson() {
        File(root, "7").mkdirs()
        File(root, "loose.txt").writeText("not a directory")
        assertEquals(listOf("7"), root.usedIds().toList())
    }

    @Test
    fun tryClaimIdSucceedsOnceAndOnlyOnce() {
        assertNotNull(tryClaimId(root, "5"), "the first claim must win")
        assertNull(tryClaimId(root, "5"), "a second claim on the same id must lose")
    }

    @Test
    fun aWriteThatDiesMidStreamLeavesThePreviousResult() {
        // #166: compile.json and result.json were written in place, and outputStream()
        // truncates on open, so a write that died partway left a torn file that
        // lastCompileResult() / lastRenderResult() then threw on.
        val target = File(root, "compile.json")
        writeInfo(target, json, TaskResult(status = TaskStatus.SUCCESS))

        assertFailsWith<IOException> {
            writeInfo(target, json, DiesMidWrite())
        }

        val read = target.inputStream().use { json.decodeFromStream<TaskResult>(it) }
        assertEquals(TaskResult(status = TaskStatus.SUCCESS), read)
    }
}

/**
 * Encodes its opening field, then throws, standing in for a process killed mid-write. The
 * serializer is written by hand because the backend's tests are not compiled with the
 * serialization plugin; `serializer<T>()` finds it through the companion.
 */
internal class DiesMidWrite {
    companion object {
        fun serializer(): KSerializer<DiesMidWrite> = Killed
    }

    private object Killed : KSerializer<DiesMidWrite> {
        override val descriptor = buildClassSerialDescriptor("DiesMidWrite") {
            element<String>("status")
            element<String>("messages")
        }

        override fun serialize(encoder: Encoder, value: DiesMidWrite) =
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, "FAILURE")
                throw IOException("killed mid-write")
            }

        override fun deserialize(decoder: Decoder): DiesMidWrite = error("write-only")
    }
}
