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

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.testutil.TestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * konstructor#155: a konstruction whose info.json can't be decoded, opened directly by id
 * (since #154 the UI never lists one). Every call used to throw before its body ran,
 * because each one evaluated a call sign that was built from info.json.
 */
class UnreadableInfoKonstructionTest {

    private lateinit var env: TestEnvironment
    private lateinit var konstructor: KonstructorImpl
    private lateinit var konstruction: Konstruction

    @BeforeTest
    fun setUp() = runBlocking {
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test Workspace")
        konstructor = KonstructorImpl(env.config)
        konstruction = WorkspaceImpl(env.config, "ws1")
            .create(Konstruction(name = "cube", workspaceId = "ws1", id = ""))
        env.pathController["ws1", konstruction.id].contentFile.writeText(CONTENT)
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    private fun breakInfo() {
        env.pathController["ws1", konstruction.id].infoFile.writeText(TRUNCATED_INFO)
    }

    @Test
    fun closeRunsItsCleanupAndEvictsTheService() = runBlocking {
        breakInfo()
        val service = konstructor.konstruction(konstruction)
        assertSame(service, konstructor.konstruction(konstruction), "precondition: cached")

        service.close()

        assertNotSame(
            service,
            konstructor.konstruction(konstruction),
            "close() must remove the service from konstructionLookup"
        )
    }

    @Test
    fun fetchDoesNotNeedInfo() = runBlocking {
        breakInfo()
        assertEquals(CONTENT, konstructor.konstruction(konstruction).fetch())
    }

    @Test
    fun callsThatNeedInfoNameTheUnreadableFile() = runBlocking {
        breakInfo()
        val error = assertFailsWith<IllegalStateException> {
            konstructor.konstruction(konstruction).getInfo()
        }
        val infoFile = env.pathController["ws1", konstruction.id].infoFile
        assertTrue(infoFile.path in error.message.orEmpty(), "message: ${error.message}")
    }

    @Test
    fun aRepairedInfoJsonIsReadAgain() = runBlocking {
        val infoFile = env.pathController["ws1", konstruction.id].infoFile
        val good = infoFile.readText()
        breakInfo()
        val service = konstructor.konstruction(konstruction)
        assertFailsWith<IllegalStateException> { service.getInfo() }

        infoFile.writeText(good)

        assertEquals("cube", service.getName())
    }

    // Control: a readable konstruction behaves exactly as before.
    @Test
    fun aReadableKonstructionStillWorksAndCloses() = runBlocking {
        val service = konstructor.konstruction(konstruction)
        assertEquals(CONTENT, service.fetch())
        assertEquals("cube", service.getName())
        assertEquals(konstruction, service.getInfo().konstruction)
        assertSame(service, konstructor.konstruction(konstruction))

        service.close()

        assertNotSame(service, konstructor.konstruction(konstruction))
    }

    private companion object {
        const val CONTENT = "val x = 1\n"
        const val TRUNCATED_INFO = """{"konstruction":{"name":"unrea"""
    }
}
