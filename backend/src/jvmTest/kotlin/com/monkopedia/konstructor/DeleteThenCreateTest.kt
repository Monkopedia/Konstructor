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
import com.monkopedia.konstructor.common.KonstructionType.CSGS
import com.monkopedia.konstructor.common.KonstructionType.STL
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.testutil.TestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A konstruction or workspace created after a delete must not inherit the deleted one's
 * cached state (konstructor#165).
 *
 * Controllers are cached per `(workspaceId, id)` for the process lifetime (#126), so an id
 * handed out again after a delete served the deleted item's name, type and targets, and the
 * first edit persisted them over the new item's info.json. Ids are therefore never reused.
 */
class DeleteThenCreateTest {

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
    fun konstructionCreatedAfterDeleteDoesNotInheritCachedInfo() = runBlocking {
        val ws = konstructor.create(Space(id = "", name = "ws"))
        val workspace = konstructor.get(ws.id)

        val old = workspace.create(Konstruction("old-part", ws.id, "", CSGS))
        val oldService = konstructor.konstruction(old)
        // Positive control: the controller for `old` is cached and serving its info.
        assertEquals("old-part", oldService.getInfo().konstruction.name)
        oldService.close()
        workspace.delete(old)

        val fresh = workspace.create(Konstruction("new-stl", ws.id, "", STL))
        assertNotEquals(old.id, fresh.id, "a deleted konstruction's id was handed out again")

        val service = konstructor.konstruction(fresh)
        assertEquals(fresh, service.getInfo().konstruction)
        service.set("// edit")
        assertEquals(fresh, service.getInfo().konstruction)
        assertEquals(listOf(fresh), workspace.list())
    }

    @Test
    fun workspaceCreatedAfterDeleteDoesNotInheritCachedKonstructions() = runBlocking {
        val oldWs = konstructor.create(Space(id = "", name = "old-ws"))
        val old = konstructor.get(oldWs.id).create(Konstruction("old-part", oldWs.id, "", CSGS))
        // Positive control, and left open: a client still holding it must not leak it on.
        assertEquals("old-part", konstructor.konstruction(old).getInfo().konstruction.name)
        konstructor.delete(oldWs)

        val freshWs = konstructor.create(Space(id = "", name = "new-ws"))
        assertNotEquals(oldWs.id, freshWs.id, "a deleted workspace's id was handed out again")
        val workspace = konstructor.get(freshWs.id)
        assertEquals("new-ws", workspace.getName())
        assertTrue(workspace.list().isEmpty())

        val fresh = workspace.create(Konstruction("new-stl", freshWs.id, "", STL))
        val service = konstructor.konstruction(fresh)
        assertEquals(fresh, service.getInfo().konstruction)
        service.set("// edit")
        assertEquals(listOf(fresh), workspace.list())
    }

    @Test
    fun deletingTheHighestIdDoesNotFreeItForReuse() = runBlocking {
        val ws = konstructor.create(Space(id = "", name = "ws"))
        val workspace = konstructor.get(ws.id)
        val first = workspace.create(Konstruction("first", ws.id, ""))
        val second = workspace.create(Konstruction("second", ws.id, ""))
        workspace.delete(second)

        val third = workspace.create(Konstruction("third", ws.id, ""))
        assertTrue(third.id !in setOf(first.id, second.id), "reused id ${third.id}")
        assertEquals(setOf("first", "third"), workspace.list().map { it.name }.toSet())
    }
}
