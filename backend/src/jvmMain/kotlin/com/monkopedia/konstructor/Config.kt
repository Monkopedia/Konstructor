package com.monkopedia.konstructor

import kotlinx.serialization.json.Json
import java.io.File

class Config {

    val json: Json
        get() = Json
    val dataDir: File by lazy {
        File(File(System.getenv("HOME")), ".konstructor").also { it.mkdirs() }
    }
}
