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

import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.junit.After
import org.junit.Before

class KonstructionControllerImplTest {

    private lateinit var env: TestEnvironment
    private lateinit var controller: KonstructionControllerImpl

    @Before
    fun setUp() {
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test")
        createKonstructionInfo("ws1", "k1", "test-k")
        controller = KonstructionControllerImpl(env.config, "ws1", "k1", Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun createKonstructionInfo(workspaceId: String, id: String, name: String) {
        val dir = File(env.tempDir, "$workspaceId/$id")
        dir.mkdirs()
        val info = KonstructionInfo(
            Konstruction(name = name, workspaceId = workspaceId, id = id),
            DirtyState.CLEAN
        )
        File(dir, "info.json").outputStream().use {
            env.config.json.encodeToStream(info, it)
        }
    }

    @Test
    fun testReadEmptyContent() {
        val result = controller.read()
        assertEquals("", result)
    }

    @Test
    fun testWriteAndRead() = runBlocking {
        controller.write("hello")
        val result = controller.read()
        assertEquals("hello", result)
        Unit
    }

    @Test
    fun testInfoLoadsFromFile() {
        assertEquals("test-k", controller.info.konstruction.name)
    }

    @Test
    fun testPathsPointToCorrectDir() {
        assertEquals("ws1", controller.paths.workspaceId)
        assertEquals("k1", controller.paths.konstructionId)
    }

    @Test
    fun testCopyContentToScript() {
        val userCode = "val x = 1"
        val input = userCode.byteInputStream()
        val output = File(env.tempDir, "test.kt")
        KonstructionControllerImpl.copyContentToScript(input, output)
        val content = output.readText()
        assertTrue(content.contains("fun main"))
        assertTrue(content.contains("val x = 1"))
    }
}
