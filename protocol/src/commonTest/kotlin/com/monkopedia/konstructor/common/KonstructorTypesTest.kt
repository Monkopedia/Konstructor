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
package com.monkopedia.konstructor.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class KonstructorTypesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSpaceRoundTrip() {
        val space = Space(id = "ws1", name = "My Workspace")
        val encoded = json.encodeToString(Space.serializer(), space)
        val decoded = json.decodeFromString(Space.serializer(), encoded)
        assertEquals(space, decoded)
    }

    @Test
    fun testKonstructionRoundTrip() {
        val k = Konstruction(
            name = "test",
            workspaceId = "ws1",
            id = "k1"
        )
        val encoded = json.encodeToString(Konstruction.serializer(), k)
        val decoded = json.decodeFromString(Konstruction.serializer(), encoded)
        assertEquals(k, decoded)
    }

    @Test
    fun testKonstructionDefaultType() {
        val k = Konstruction(name = "t", workspaceId = "w", id = "k")
        assertEquals(KonstructionType.CSGS, k.type)
    }

    @Test
    fun testKonstructionStlType() {
        val k = Konstruction(
            name = "t",
            workspaceId = "w",
            id = "k",
            type = KonstructionType.STL
        )
        val encoded = json.encodeToString(Konstruction.serializer(), k)
        val decoded = json.decodeFromString(Konstruction.serializer(), encoded)
        assertEquals(KonstructionType.STL, decoded.type)
    }

    @Test
    fun testKonstructionInfoRoundTrip() {
        val info = KonstructionInfo(
            konstruction = Konstruction("n", "w", "k"),
            dirtyState = DirtyState.NEEDS_COMPILE,
            targets = listOf(
                KonstructionTarget("cube", DirtyState.CLEAN),
                KonstructionTarget("sphere", DirtyState.NEEDS_EXEC)
            )
        )
        val encoded = json.encodeToString(KonstructionInfo.serializer(), info)
        val decoded = json.decodeFromString(KonstructionInfo.serializer(), encoded)
        assertEquals(info, decoded)
    }

    @Test
    fun testKonstructionInfoDefaultTargets() {
        val info = KonstructionInfo(
            konstruction = Konstruction("n", "w", "k"),
            dirtyState = DirtyState.CLEAN
        )
        assertEquals(emptyList(), info.targets)
    }

    @Test
    fun testKonstructionRenderRoundTrip() {
        val render = KonstructionRender(
            konstruction = Konstruction("n", "w", "k"),
            name = "cube",
            renderPath = "/tmp/render.stl"
        )
        val encoded = json.encodeToString(KonstructionRender.serializer(), render)
        val decoded = json.decodeFromString(KonstructionRender.serializer(), encoded)
        assertEquals(render, decoded)
    }

    @Test
    fun testKonstructionRenderNullPath() {
        val render = KonstructionRender(
            konstruction = Konstruction("n", "w", "k"),
            name = "cube",
            renderPath = null
        )
        val encoded = json.encodeToString(KonstructionRender.serializer(), render)
        val decoded = json.decodeFromString(KonstructionRender.serializer(), encoded)
        assertEquals(null, decoded.renderPath)
    }

    @Test
    fun testTaskResultRoundTrip() {
        val result = TaskResult(
            taskArguments = listOf("cube", "sphere"),
            status = TaskStatus.SUCCESS,
            messages = listOf(
                TaskMessage("all good", importance = MessageImportance.INFO)
            )
        )
        val encoded = json.encodeToString(TaskResult.serializer(), result)
        val decoded = json.decodeFromString(TaskResult.serializer(), encoded)
        assertEquals(result, decoded)
    }

    @Test
    fun testTaskMessageDefaults() {
        val msg = TaskMessage(message = "error occurred")
        assertEquals(MessageImportance.ERROR, msg.importance)
        assertEquals(null, msg.line)
        assertEquals(null, msg.char)
    }

    @Test
    fun testTaskMessageWithLineInfo() {
        val msg = TaskMessage(
            message = "syntax error",
            line = 10,
            char = 5,
            importance = MessageImportance.WARNING
        )
        val encoded = json.encodeToString(TaskMessage.serializer(), msg)
        val decoded = json.decodeFromString(TaskMessage.serializer(), encoded)
        assertEquals(msg, decoded)
    }

    @Test
    fun testDirtyStateValues() {
        val values = DirtyState.entries
        assertEquals(3, values.size)
        assertEquals(DirtyState.CLEAN, values[0])
        assertEquals(DirtyState.NEEDS_COMPILE, values[1])
        assertEquals(DirtyState.NEEDS_EXEC, values[2])
    }

    @Test
    fun testKonstructionCallbacksValues() {
        val values = KonstructionCallbacks.entries
        assertEquals(6, values.size)
    }
}
