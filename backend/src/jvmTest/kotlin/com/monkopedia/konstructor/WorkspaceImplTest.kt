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
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class WorkspaceImplTest {

    private lateinit var env: TestEnvironment
    private lateinit var workspace: WorkspaceImpl

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
        env.createWorkspaceDir("ws1", "Test Workspace")
        workspace = WorkspaceImpl(env.config, "ws1")
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    @Test
    fun testListEmptyKonstructions() = runBlocking {
        val result = workspace.list()
        assertTrue(result.isEmpty())
    }

    @Test
    fun testCreateKonstruction() = runBlocking {
        val created = workspace.create(Konstruction(name = "cube", workspaceId = "ws1", id = ""))
        assertTrue(created.id.isNotEmpty())
        assertEquals("cube", created.name)
        assertEquals("ws1", created.workspaceId)
    }

    @Test
    fun testListAfterCreate() = runBlocking {
        workspace.create(Konstruction(name = "cube", workspaceId = "ws1", id = ""))
        val result = workspace.list()
        assertEquals(1, result.size)
    }

    @Test
    fun testDeleteKonstruction() = runBlocking {
        val created = workspace.create(Konstruction(name = "cube", workspaceId = "ws1", id = ""))
        workspace.delete(created)
        val result = workspace.list()
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetName() = runBlocking {
        val name = workspace.getName()
        assertEquals("Test Workspace", name)
    }

    @Test
    fun testSetName() = runBlocking {
        workspace.setName("New Name")
        val name = workspace.getName()
        assertEquals("New Name", name)
    }

    @Test
    fun testCreateWrongWorkspaceThrows() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            workspace.create(Konstruction(name = "cube", workspaceId = "other", id = ""))
        }
        Unit
    }

    // konstructor#140. Each payload below is an info.json this backend cannot decode. The
    // case that matters is a ROLLBACK: a newer backend persists a DirtyState this one does
    // not have (RENDER_FAILED from #138, COMPILE_FAILED from #151) — simulated here with a
    // name that is in no version's enum. One such file used to throw out of list() and
    // take every other konstruction in the workspace with it.

    @Test
    fun anUnknownDirtyStateDegradesOnlyThatKonstruction() = assertOnlyTheReadableOnesList(
        """{"konstruction":$BAD_KONSTRUCTION,"dirtyState":"$FUTURE_STATE"}"""
    )

    @Test
    fun anUnknownTargetStateDegradesOnlyThatKonstruction() = assertOnlyTheReadableOnesList(
        """{"konstruction":$BAD_KONSTRUCTION,"dirtyState":"CLEAN",""" +
            """"targets":[{"name":"cube","state":"$FUTURE_STATE"}]}"""
    )

    @Test
    fun aMissingDirtyStateDegradesOnlyThatKonstructionRatherThanReadingAsClean() =
        assertOnlyTheReadableOnesList("""{"konstruction":$BAD_KONSTRUCTION}""")

    @Test
    fun aTruncatedInfoJsonDegradesOnlyThatKonstruction() =
        assertOnlyTheReadableOnesList("""{"konstruction":{"name":"unrea""")

    private fun assertOnlyTheReadableOnesList(unreadableInfo: String) = runBlocking {
        workspace.create(Konstruction(name = "before", workspaceId = "ws1", id = ""))
        val badDir = File(File(env.tempDir, "ws1"), "1").also { it.mkdirs() }
        File(badDir, "info.json").writeText(unreadableInfo)
        workspace.create(Konstruction(name = "after", workspaceId = "ws1", id = "2"))

        assertEquals(listOf("after", "before"), workspace.list().map { it.name }.sorted())
        // Degraded, not deleted: the file is untouched (a roll-forward reads it again), and
        // its id stays taken, so a new konstruction cannot be created on top of it.
        assertEquals(unreadableInfo, File(badDir, "info.json").readText())
        assertEquals(
            "3",
            workspace.create(Konstruction(name = "new", workspaceId = "ws1", id = "")).id
        )
    }

    private companion object {
        const val FUTURE_STATE = "STATE_FROM_A_NEWER_BACKEND"
        const val BAD_KONSTRUCTION = """{"name":"unreadable","workspaceId":"ws1","id":"1"}"""
    }
}
