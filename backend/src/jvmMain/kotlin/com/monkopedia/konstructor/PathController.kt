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

import java.io.File

class PathController private constructor(private val config: Config) {

    data class Paths(
        val workspaceId: String,
        val konstructionId: String,
        val workspaceDir: File,
        val infoFile: File,
        val compileResultFile: File,
        val compileOutput: File,
        val renderResultFile: File,
        val renderOutput: File,
        val contentFile: File,
        val kotlinFile: File,
        val cacheDir: File,
    )

    operator fun get(workspaceId: String, id: String): Paths {
        val workspaceDir = File(config.dataDir, workspaceId)
        val scriptDir = File(workspaceDir, id).also { it.mkdirs() }
        return Paths(
            workspaceId = workspaceId,
            konstructionId = id,
            workspaceDir = File(config.dataDir, workspaceId),
            infoFile = File(scriptDir, "info.json"),
            compileResultFile = File(scriptDir, "compile.json"),
            compileOutput = File(scriptDir, "out"),
            renderResultFile = File(scriptDir, "result.json"),
            renderOutput = File(scriptDir, "renders"),
            contentFile = File(scriptDir, "content.csgs"),
            kotlinFile = File(scriptDir, "content.kt"),
            cacheDir = File(scriptDir, "cacheDir")
        )
    }

    companion object : (Config) -> PathController {
        private val managers = mutableMapOf<Config, PathController>()

        override fun invoke(config: Config): PathController {
            synchronized(managers) {
                return managers.getOrPut(config) {
                    PathController(config)
                }
            }
        }
    }
}
