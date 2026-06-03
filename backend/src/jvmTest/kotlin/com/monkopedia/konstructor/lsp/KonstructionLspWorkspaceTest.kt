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
package com.monkopedia.konstructor.lsp

import com.monkopedia.konstructor.Config
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.tasks.LibsJar
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Verifies the synthesized per-konstruction kotlin-lsp workspace: the
 * `workspace.json` matches the strict schema kotlin-lsp expects (the PoC recipe),
 * and the wrapped `.kt` reuses konstructor's exact compiler wrapping.
 *
 * Pure file/JSON synthesis — no subprocess, so this IS CI-safe.
 */
class KonstructionLspWorkspaceTest {

    private val tempDir = createTempDirectory("lsp-ws-test-").toFile()
    private val config = Config(tempDir)

    @BeforeTest
    fun primeLibsJar() {
        // Prime the process-wide LibsJar cache to a stub jar so synthesis doesn't try to
        // extract the bundled `/lib-all.raj` resource (absent in a plain `:backend:test`).
        // workspace.json only needs the PATH; the engine (full build) reads the real jar.
        val stubJar = File(config.dataDir, "lib.jar").apply { writeText("stub") }
        val field = LibsJar::class.java.getDeclaredField("libsFile")
        field.isAccessible = true
        field.set(LibsJar, stubJar)
    }

    @AfterTest
    fun cleanup() {
        val field = LibsJar::class.java.getDeclaredField("libsFile")
        field.isAccessible = true
        field.set(LibsJar, null)
        tempDir.deleteRecursively()
    }

    @Test
    fun synthesizeWritesWrappedKtAndWorkspaceJson() {
        val workspaceId = "ws1"
        val konstructionId = "kon1"
        val paths = PathController(config)[workspaceId, konstructionId]
        paths.contentFile.writeText(
            """
            val box by primitive { cube { dimensions = xyz(1.0, 1.0, 1.0) } }
            export("box")
            """.trimIndent()
        )

        val ws = KonstructionLspWorkspace(config, workspaceId, konstructionId)
        ws.synthesize()

        // Wrapped .kt exists, carries the compiler wrapping (header swap + user body).
        assertTrue(ws.wrappedFile.exists(), "wrapped .kt must be written")
        val wrapped = ws.wrappedText()
        assertTrue(
            wrapped.contains("runKonstruction"),
            "wrapped .kt must contain konstructor's main-invocation header"
        )
        assertTrue(wrapped.contains("export(\"box\")"), "wrapped .kt must contain the user body")

        // workspace.json parses and matches the PoC schema exactly.
        val jsonFile = ws.wrappedFile.parentFile.resolve("workspace.json")
        assertTrue(jsonFile.exists(), "workspace.json must be written")
        val root = Json.parseToString(jsonFile.readText())

        val module = root["modules"]!!.jsonArray.single().jsonObject
        assertEquals("JAVA_MODULE", module["type"]!!.jsonPrimitive.content)
        val deps = module["dependencies"]!!.jsonArray
        // Polymorphic discriminator is the inline `type` field, lowercase values.
        assertEquals("moduleSource", deps[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("library", deps[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("kcsg-lib", deps[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("compile", deps[1].jsonObject["scope"]!!.jsonPrimitive.content)

        val sourceRoot = module["contentRoots"]!!.jsonArray.single()
            .jsonObject["sourceRoots"]!!.jsonArray.single().jsonObject
        assertEquals("java-source", sourceRoot["type"]!!.jsonPrimitive.content)
        assertEquals(ws.workspaceDir.absolutePath, sourceRoot["path"]!!.jsonPrimitive.content)

        val library = root["libraries"]!!.jsonArray.single().jsonObject
        assertEquals("kcsg-lib", library["name"]!!.jsonPrimitive.content)
        assertEquals("java-imported", library["type"]!!.jsonPrimitive.content)
        assertEquals("project", library["level"]!!.jsonPrimitive.content)
        val libraryRoot = library["roots"]!!.jsonArray.single().jsonObject
        assertEquals("CLASSES", libraryRoot["type"]!!.jsonPrimitive.content)
        assertEquals("root_itself", libraryRoot["inclusionOptions"]!!.jsonPrimitive.content)
        assertTrue(
            libraryRoot["path"]!!.jsonPrimitive.content.endsWith("lib.jar"),
            "library root must point at the resolved lib.jar"
        )
    }

    private fun Json.parseToString(text: String) = parseToJsonElement(text).jsonObject
}
