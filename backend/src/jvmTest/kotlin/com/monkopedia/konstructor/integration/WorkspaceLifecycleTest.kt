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
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.testutil.TestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class WorkspaceLifecycleTest {

    private lateinit var env: TestEnvironment
    private lateinit var service: KonstructorImpl

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
        service = KonstructorImpl(env.config)
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    @Test
    fun testCreateListDeleteWorkspace() = runBlocking {
        val created = service.create(Space(id = "", name = "Test"))
        val listAfterCreate = service.list()
        assertEquals(1, listAfterCreate.size)
        assertEquals("Test", listAfterCreate[0].name)

        service.delete(created)
        val listAfterDelete = service.list()
        assertTrue(listAfterDelete.isEmpty())
    }

    @Test
    fun testCreateMultipleWorkspaces() = runBlocking {
        service.create(Space(id = "", name = "Alpha"))
        service.create(Space(id = "", name = "Beta"))
        service.create(Space(id = "", name = "Gamma"))

        val result = service.list()
        assertEquals(3, result.size)
    }

    @Test
    fun testWorkspaceNameOperations() = runBlocking {
        val created = service.create(Space(id = "", name = "Original"))
        val workspace = service.get(created.id) as WorkspaceImpl

        assertEquals("Original", workspace.getName())

        workspace.setName("New")
        assertEquals("New", workspace.getName())
    }
}
