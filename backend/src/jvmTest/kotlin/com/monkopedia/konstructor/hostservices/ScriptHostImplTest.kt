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
package com.monkopedia.konstructor.hostservices

import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before

class ScriptHostImplTest {

    private lateinit var env: TestEnvironment
    private lateinit var host: ScriptHostImpl
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        env = TestEnvironment()
        cacheDir = File(env.tempDir, "cache")
        host = ScriptHostImpl(
            scriptManager = ScriptManager(env.config),
            config = env.config,
            workspaceId = "ws1",
            konstructionId = "k1",
            cacheDir = cacheDir
        )
    }

    @After
    fun tearDown() {
        env.close()
    }

    @Test
    fun testSupportsCaching() = runBlocking {
        val result = host.supportsCaching(Unit)
        assertTrue(result)
        Unit
    }

    @Test
    fun testCheckCachedMiss() = runBlocking {
        val result = host.checkCached("nonexistent")
        assertNull(result)
        Unit
    }

    @Test
    fun testCheckCachedHit() = runBlocking {
        cacheDir.mkdirs()
        File(cacheDir, "abc.stl").writeText("stl data")
        val result = host.checkCached("abc")
        assertNotNull(result)
        assertTrue(result.endsWith("abc.stl"))
        Unit
    }

    @Test
    fun testStoreCached() = runBlocking {
        val result = host.storeCached("xyz")
        assertTrue(result.endsWith("xyz.stl"))
        Unit
    }

    @Test
    fun testCacheDirCreatedOnInit() {
        assertTrue(cacheDir.exists())
    }
}
