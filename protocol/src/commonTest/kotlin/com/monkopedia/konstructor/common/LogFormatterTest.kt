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

import com.monkopedia.hauler.Box
import com.monkopedia.hauler.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.test.runTest

class LogFormatterTest {

    private suspend fun format(box: Box): List<String> {
        val results = mutableListOf<String>()
        val collector = FlowCollector<String> { results.add(it) }
        LogFormatter.invoke(collector, box)
        return results
    }

    @Test
    fun testFormatSingleLineMessage() = runTest {
        val box = Box(
            timestamp = 1700000000000L,
            level = Level.INFO,
            loggerName = "TestLogger",
            threadName = "main",
            message = "hello world"
        )
        val lines = format(box)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("TestLogger"))
        assertTrue(lines[0].contains("hello world"))
        assertTrue(lines[0].contains("INFO"))
    }

    @Test
    fun testFormatMultiLineMessage() = runTest {
        val box = Box(
            timestamp = 1700000000000L,
            level = Level.ERROR,
            loggerName = "TestLogger",
            threadName = "main",
            message = "line1\nline2\nline3"
        )
        val lines = format(box)
        assertEquals(3, lines.size)
        assertTrue(lines[0].endsWith("line1"))
        assertTrue(lines[1].endsWith("line2"))
        assertTrue(lines[2].endsWith("line3"))
    }

    @Test
    fun testFormatWithThreadName() = runTest {
        val box = Box(
            timestamp = 1700000000000L,
            level = Level.DEBUG,
            loggerName = "Logger",
            threadName = "worker-1",
            message = "msg"
        )
        val lines = format(box)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("(worker-1)"))
    }

    @Test
    fun testFormatWithoutThreadName() = runTest {
        val box = Box(
            timestamp = 1700000000000L,
            level = Level.WARN,
            loggerName = "Logger",
            threadName = null,
            message = "msg"
        )
        val lines = format(box)
        assertEquals(1, lines.size)
        assertTrue(!lines[0].contains("("))
        assertTrue(lines[0].contains("WARN"))
        assertTrue(lines[0].contains("Logger"))
    }
}
