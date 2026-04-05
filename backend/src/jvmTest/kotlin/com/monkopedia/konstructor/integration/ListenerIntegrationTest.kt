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

package com.monkopedia.konstructor.integration

import com.monkopedia.konstructor.KonstructionServiceImpl
import com.monkopedia.konstructor.common.DirtyState
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionInfo
import com.monkopedia.konstructor.logging.WarehouseWrapper
import com.monkopedia.konstructor.testutil.FakeKonstructionListener
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream

class ListenerIntegrationTest {

    private lateinit var env: TestEnvironment
    private lateinit var service: KonstructionServiceImpl

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test")
        val dir = File(env.tempDir, "ws1/k1")
        dir.mkdirs()
        val info = KonstructionInfo(
            Konstruction(name = "test", workspaceId = "ws1", id = "k1"),
            DirtyState.CLEAN
        )
        File(dir, "info.json").outputStream().use {
            env.config.json.encodeToStream(info, it)
        }
        service = KonstructionServiceImpl(
            config = env.config,
            workspaceId = "ws1",
            id = "k1",
            warehouseWrapper = WarehouseWrapper(),
            onClose = {}
        )
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    @Test
    fun testRegisterAndUnregisterListener() = runBlocking {
        val listener = FakeKonstructionListener()
        val key = service.register(listener)
        assertTrue(key.isNotEmpty(), "Registration key should be non-empty")

        val removed = service.unregister(key)
        assertTrue(removed, "Unregister should return true for valid key")
    }

    @Test
    fun testUnregisterInvalidKey() = runBlocking {
        val removed = service.unregister("nonexistent")
        assertFalse(removed, "Unregister should return false for invalid key")
    }

    @Test
    fun testListenerReceivesContentChange() = runBlocking {
        val listener = FakeKonstructionListener()
        val key = service.register(listener)
        service.set("code")
        delay(200)
        assertTrue(
            listener.contentChanges.isNotEmpty(),
            "Listener should have received content change callbacks"
        )
        service.unregister(key)
        Unit
    }
}
