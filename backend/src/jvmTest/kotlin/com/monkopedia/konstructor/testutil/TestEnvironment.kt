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
@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.konstructor.testutil

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.common.Space
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

class TestEnvironment : AutoCloseable {

    val tempDir: File = kotlin.io.path.createTempDirectory("konstructor-test-").toFile()
    val config: Config = Config(tempDir)
    val pathController: PathController = PathController(config)

    private val json = Json { ignoreUnknownKeys = true }

    fun createWorkspaceDir(workspaceId: String, name: String): Space {
        val space = Space(id = workspaceId, name = name)
        val workspaceDir = File(tempDir, workspaceId)
        workspaceDir.mkdirs()
        val infoFile = File(workspaceDir, "info.json")
        infoFile.outputStream().use { output ->
            json.encodeToStream(space, output)
        }
        return space
    }

    override fun close() {
        tempDir.deleteRecursively()
    }
}
