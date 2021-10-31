package com.monkopedia.konstructor

import com.monkopedia.konstructor.tasks.LibsJar
import kotlinx.serialization.json.Json
import java.io.File

class Config {

    val compilerOpts: String
        get() = "-cp ${LibsJar.getLibsJar(this).absolutePath}"
    val runtimeOpts: String
        get() = ""
    val json: Json
        get() = Json
    val dataDir: File by lazy {
        File(File(System.getenv("HOME")), ".konstructor").also { it.mkdirs() }
    }
}
