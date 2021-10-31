package com.monkopedia.konstructor.tasks

import com.monkopedia.konstructor.Config
import java.io.File

object LibsJar {
    private var libsFile: File? = null

    fun getLibsJar(config: Config): File {
        return libsFile ?: createLibsFile(config).also {
            libsFile = it
        }
    }

    private fun createLibsFile(config: Config): File {
        return File(config.dataDir, "lib.jar").also {
            if (!it.parentFile.exists()) {
                it.parentFile.mkdirs()
            }
            it.outputStream().use { output ->
                this::class.java.getResourceAsStream("/lib-fat.jar").use {
                    it.copyTo(output)
                }
            }
        }
    }
}
