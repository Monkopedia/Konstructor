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
package com.monkopedia.konstructor.integration

import com.monkopedia.konstructor.KonstructorImpl
import com.monkopedia.konstructor.WorkspaceImpl
import com.monkopedia.konstructor.common.DirtyState.NEEDS_COMPILE
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.testutil.TestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class KonstructionLifecycleTest {

    private lateinit var env: TestEnvironment
    private lateinit var konstructor: KonstructorImpl

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
        konstructor = KonstructorImpl(env.config)
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    @Test
    fun testCreateListDeleteKonstruction() = runBlocking {
        val ws = konstructor.create(Space(id = "", name = "Test"))
        val workspace = konstructor.get(ws.id) as WorkspaceImpl

        val k = workspace.create(Konstruction(name = "cube", workspaceId = ws.id, id = ""))
        val listAfterCreate = workspace.list()
        assertEquals(1, listAfterCreate.size)
        assertEquals("cube", listAfterCreate[0].name)

        workspace.delete(k)
        val listAfterDelete = workspace.list()
        assertTrue(listAfterDelete.isEmpty())
    }

    @Test
    fun testKonstructionContentSetAndFetch() = runBlocking {
        val ws = konstructor.create(Space(id = "", name = "Test"))
        val workspace = konstructor.get(ws.id) as WorkspaceImpl
        val k = workspace.create(Konstruction(name = "cube", workspaceId = ws.id, id = ""))

        val ksService = konstructor.konstruction(k)
        ksService.set("some code")
        val fetched = ksService.fetch()
        assertEquals("some code", fetched)
    }

    @Test
    fun testDirtyStateAfterContentChange() = runBlocking {
        val ws = konstructor.create(Space(id = "", name = "Test"))
        val workspace = konstructor.get(ws.id) as WorkspaceImpl
        val k = workspace.create(Konstruction(name = "cube", workspaceId = ws.id, id = ""))

        val ksService = konstructor.konstruction(k)
        ksService.set("val x = 1")
        val info = ksService.getInfo()
        assertEquals(NEEDS_COMPILE, info.dirtyState)
    }
}
