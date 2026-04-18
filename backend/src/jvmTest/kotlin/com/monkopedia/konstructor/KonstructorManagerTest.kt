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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import org.junit.After
import org.junit.Before

class KonstructorManagerTest {

    private lateinit var env: TestEnvironment
    private lateinit var manager: KonstructorManager

    @Before
    fun setUp() {
        env = TestEnvironment()
        manager = KonstructorManager(env.config)
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
    fun testSameIdReturnsSameController() {
        env.createWorkspaceDir("ws1", "Workspace 1")
        createKonstructionInfo("ws1", "k1", "test-k")
        val controller1 = manager.controllerFor("ws1", "k1")
        val controller2 = manager.controllerFor("ws1", "k1")
        assertSame(controller1, controller2)
    }

    @Test
    fun testDifferentIdReturnsDifferentController() {
        env.createWorkspaceDir("ws1", "Workspace 1")
        createKonstructionInfo("ws1", "k1", "test-k1")
        createKonstructionInfo("ws1", "k2", "test-k2")
        val controller1 = manager.controllerFor("ws1", "k1")
        val controller2 = manager.controllerFor("ws1", "k2")
        assertNotSame(controller1, controller2)
    }
}
