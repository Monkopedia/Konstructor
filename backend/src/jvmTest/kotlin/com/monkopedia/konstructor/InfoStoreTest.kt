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
import kotlinx.serialization.ExperimentalSerializationApi
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
    fun listInfoDecodesEveryItemDirectory() {
        writeSpace("0")
        writeSpace("1")
        assertEquals(
            listOf("0", "1"),
            root.listInfo<Space>(json).map { it.id }.sorted()
        )
    }

    @Test
    fun listInfoSkipsLooseFilesAndDirectoriesWithoutInfo() {
        writeSpace("0")
        File(root, "stray.txt").writeText("not a workspace")
        File(root, "half-created").mkdirs()
        assertEquals(listOf("0"), root.listInfo<Space>(json).map { it.id })
    }

    @Test
    fun listInfoOnMissingDirectoryIsEmpty() {
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
    fun writeNewInfoWritesTheItemAndRejectsAReusedId() {
        val target = File(File(root, "0"), INFO_JSON)
        writeNewInfo(target, "0", json, Space(id = "0", name = "first"))
        val written = target.inputStream().use { json.decodeFromStream<Space>(it) }
        assertEquals("first", written.name)

        val failure = assertFailsWith<IllegalArgumentException> {
            writeNewInfo(target, "0", json, Space(id = "0", name = "second"))
        }
        assertEquals("0 has been used already", failure.message)
    }

    @Test
    fun writeNewInfoRejectsAnIdWhoseDirectoryExistsWithoutInfo() {
        File(root, "7").mkdirs()
        assertFailsWith<IllegalArgumentException> {
            writeNewInfo(File(File(root, "7"), INFO_JSON), "7", json, Space(id = "7", name = "x"))
        }
    }
}
