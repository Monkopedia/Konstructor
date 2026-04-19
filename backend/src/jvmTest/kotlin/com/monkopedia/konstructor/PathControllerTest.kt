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

import com.monkopedia.konstructor.testutil.TestEnvironment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathControllerTest {

    private lateinit var env: TestEnvironment

    @BeforeTest
    fun setUp() {
        env = TestEnvironment()
    }

    @AfterTest
    fun tearDown() {
        env.close()
    }

    @Test
    fun testPathsCreatesDirectory() {
        val paths = env.pathController["ws1", "k1"]
        assertTrue(paths.workspaceDir.exists() || paths.workspaceDir.parentFile.exists())
        // The get operator calls mkdirs on the script dir
        val scriptDir = paths.infoFile.parentFile
        assertTrue(scriptDir.exists())
    }

    @Test
    fun testInfoFilePath() {
        val paths = env.pathController["ws1", "k1"]
        assertTrue(paths.infoFile.path.endsWith("ws1/k1/info.json"))
    }

    @Test
    fun testCompileOutputPath() {
        val paths = env.pathController["ws1", "k1"]
        assertTrue(paths.compileOutput.path.endsWith("ws1/k1/out"))
    }

    @Test
    fun testContentFilePath() {
        val paths = env.pathController["ws1", "k1"]
        assertTrue(paths.contentFile.path.endsWith("ws1/k1/content.csgs"))
    }

    @Test
    fun testKotlinFilePath() {
        val paths = env.pathController["ws1", "k1"]
        assertTrue(paths.kotlinFile.path.endsWith("ws1/k1/content.kt"))
    }

    @Test
    fun testCacheDirPath() {
        val paths = env.pathController["ws1", "k1"]
        assertTrue(paths.cacheDir.path.endsWith("ws1/k1/cacheDir"))
    }

    @Test
    fun testRenderOutputPath() {
        val paths = env.pathController["ws1", "k1"]
        assertTrue(paths.renderOutput.path.endsWith("ws1/k1/renders"))
    }

    @Test
    fun testPathControllerCachesInstances() {
        val pc1 = PathController(env.config)
        val pc2 = PathController(env.config)
        assertEquals(pc1, pc2)
    }
}
