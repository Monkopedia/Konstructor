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

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.tasks.LibsJar
import com.monkopedia.konstructor.testutil.TestEnvironment
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        seedLibsJar("lib-a", "kcsg 0.4.5")
        host = newHost()
    }

    private fun newHost() = ScriptHostImpl(
        scriptManager = ScriptManager(env.config),
        config = env.config,
        workspaceId = "ws1",
        konstructionId = "k1",
        cacheDir = cacheDir
    )

    /**
     * Stand in for the bundled lib jar (absent in a plain `:backend:jvmTest`) with a file of
     * [content], as if the backend had been redeployed with a different lib/kcsg build.
     * [TestEnvironment.close] clears the seeded path again.
     */
    private fun seedLibsJar(name: String, content: String) {
        val jar = File(env.tempDir, "$name.jar").apply { writeText(content) }
        LibsJar::class.java.getDeclaredField("libsFile").apply { isAccessible = true }
            .set(LibsJar, jar)
    }

    /** What the lib side does on a cache store: write geometry to the path the host hands out. */
    private suspend fun ScriptHostImpl.store(hash: String, geometry: String) {
        File(storeCached(hash)).writeText(geometry)
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
    fun testSupportsCachingFollowsConfig() = runBlocking {
        val config = Config(env.tempDir, cachingEnabled = false)
        val disabled = ScriptHostImpl(
            scriptManager = ScriptManager(config),
            config = config,
            workspaceId = "ws1",
            konstructionId = "k1",
            cacheDir = cacheDir
        )
        assertFalse(disabled.supportsCaching(Unit))
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
        host.store("abc", "stl data")
        val result = host.checkCached("abc")
        assertNotNull(result)
        assertTrue(result.endsWith("abc.stl"))
        assertEquals("stl data", File(result).readText())
        Unit
    }

    /**
     * #132: the cache key used to be the script's content hash alone, so after a kcsg/lib
     * upgrade an unchanged script kept being served the geometry the OLD library produced.
     * A different lib jar must miss.
     */
    @Test
    fun testLibChangeMissesCache() = runBlocking {
        host.store("abc", "geometry from lib-a")

        seedLibsJar("lib-b", "kcsg 0.5.0")
        assertNull(newHost().checkCached("abc"))
        Unit
    }

    /** Control for [testLibChangeMissesCache]: the same lib still hits, from a fresh host. */
    @Test
    fun testSameLibStillHitsCache() = runBlocking {
        host.store("abc", "geometry from lib-a")

        // Identical bytes at a different path (e.g. a redeploy of the same build): still a hit.
        seedLibsJar("lib-a-redeployed", "kcsg 0.4.5")
        val result = newHost().checkCached("abc")
        assertNotNull(result)
        assertEquals("geometry from lib-a", File(result).readText())
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
