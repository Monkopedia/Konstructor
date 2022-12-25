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
        return Paths(
            workspaceId = workspaceId,
            konstructionId = id,
            workspaceDir = File(config.dataDir, workspaceId),
            infoFile = File(File(workspaceDir, id), "info.json"),
            compileResultFile = File(File(workspaceDir, id), "compile.json"),
            compileOutput = File(File(workspaceDir, id), "out"),
            renderResultFile = File(File(workspaceDir, id), "result.json"),
            renderOutput = File(File(workspaceDir, id), "renders"),
            contentFile = File(File(workspaceDir, id), "content.csgs"),
            kotlinFile = File(File(workspaceDir, id), "content.kt"),
            cacheDir = File(File(workspaceDir, id), "cacheDir")
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
