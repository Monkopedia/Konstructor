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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ConfigTest {

    @Test
    fun testCustomDataDir() {
        val tempDir = kotlin.io.path.createTempDirectory("config-test-").toFile()
        try {
            val config = Config(tempDir)
            assertEquals(tempDir.absolutePath, config.dataDir.absolutePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testJsonIgnoresUnknownKeys() {
        val config = Config()
        val jsonString = """{"id":"test","name":"Test Space","unknownField":"value"}"""
        val space = config.json.decodeFromString<Space>(jsonString)
        assertEquals("test", space.id)
        assertEquals("Test Space", space.name)
    }

    @Test
    fun testExecuteTimeout() {
        val config = Config()
        assertEquals(5.minutes, config.executeTimeout)
    }

    @Test
    fun testCachingEnabled() {
        val config = Config()
        assertTrue(config.cachingEnabled)
    }
}
