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
package com.monkopedia.konstructor.tasks

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.common.MessageImportance.ERROR
import com.monkopedia.konstructor.common.MessageImportance.WARNING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompileTaskParseErrorsTest {

    private val headerLines = KcsgScript.HEADER.split("\n").size

    @Test
    fun testParseEmptyOutput() {
        val reader = "".reader().buffered()
        val result = CompileTask.parseErrors(reader)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testParseErrorPrefixed() {
        val reader = "error: something went wrong".reader().buffered()
        val result = CompileTask.parseErrors(reader)
        assertEquals(1, result.size)
        assertEquals("error: something went wrong", result[0].message)
        assertEquals(ERROR, result[0].importance)
    }

    @Test
    fun testParseWarningPrefixed() {
        val reader = "warning: deprecated".reader().buffered()
        val result = CompileTask.parseErrors(reader)
        assertEquals(1, result.size)
        assertEquals("warning: deprecated", result[0].message)
        assertEquals(WARNING, result[0].importance)
    }

    @Test
    fun testParseRegexError() {
        val line = headerLines + 5
        val reader = "file.kt:$line:5: some error message".reader().buffered()
        val result = CompileTask.parseErrors(reader)
        assertEquals(1, result.size)
        assertEquals("some error message", result[0].message)
        assertEquals(5, result[0].line)
        assertEquals(5, result[0].char)
        assertEquals(ERROR, result[0].importance)
    }

    @Test
    fun testParseRegexWarning() {
        val line = headerLines + 3
        val reader = "file.kt:$line:10: warning: something".reader().buffered()
        val result = CompileTask.parseErrors(reader)
        assertEquals(1, result.size)
        assertEquals("warning: something", result[0].message)
        assertEquals(WARNING, result[0].importance)
    }

    @Test
    fun testParseErrorInHeaderRegion() {
        val line = headerLines - 1
        val reader = "file.kt:$line:3: bad syntax".reader().buffered()
        val result = CompileTask.parseErrors(reader)
        assertEquals(1, result.size)
        assertEquals("Internal error: bad syntax", result[0].message)
        assertEquals(0, result[0].line)
        assertEquals(0, result[0].char)
    }

    @Test
    fun testParseMixedOutput() {
        val errorLine = headerLines + 1
        val input = """
            error: global error
            warning: global warning
            file.kt:$errorLine:2: actual error
        """.trimIndent()
        val reader = input.reader().buffered()
        val result = CompileTask.parseErrors(reader)
        assertEquals(3, result.size)
        assertEquals(ERROR, result[0].importance)
        assertEquals(WARNING, result[1].importance)
        assertEquals(ERROR, result[2].importance)
    }

    @Test
    fun testNonMatchingLinesIgnored() {
        val input = """
            some random output
            another line
            Compilation completed
        """.trimIndent()
        val reader = input.reader().buffered()
        val result = CompileTask.parseErrors(reader)
        assertTrue(result.isEmpty())
    }
}
