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
package com.monkopedia.konstructor

import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.testutil.TestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class KonstructorImplTest {

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
    fun testListEmptyWorkspaces() = runBlocking {
        val result = service.list()
        assertTrue(result.isEmpty())
    }

    @Test
    fun testCreateWorkspace() = runBlocking {
        val created = service.create(Space(id = "", name = "Test"))
        assertTrue(created.id.isNotEmpty())
        assertEquals("Test", created.name)
    }

    @Test
    fun testListAfterCreate() = runBlocking {
        service.create(Space(id = "", name = "Test"))
        val result = service.list()
        assertEquals(1, result.size)
    }

    @Test
    fun testDeleteWorkspace() = runBlocking {
        val created = service.create(Space(id = "", name = "Test"))
        service.delete(created)
        val result = service.list()
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetWorkspace() = runBlocking {
        service.create(Space(id = "", name = "Test"))
        val workspace = service.get("0")
        assertNotNull(workspace)
        Unit
    }

    @Test
    fun testCreateDuplicateThrows() = runBlocking {
        service.create(Space(id = "dup", name = "First"))
        assertFailsWith<IllegalArgumentException> {
            service.create(Space(id = "dup", name = "Second"))
        }
        Unit
    }

    @Test
    fun testPing() = runBlocking {
        service.ping()
    }
}
