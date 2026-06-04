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
import com.monkopedia.konstructor.KonstructionControllerImpl
import com.monkopedia.konstructor.PathController
import com.monkopedia.konstructor.tasks.LibsJar
import java.io.File
import java.net.URI
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Synthesizes the per-konstruction kotlin-lsp workspace: a directory containing
 *  - the **wrapped `.kt`** (konstructor's exact compiler wrapping, so LSP positions
 *    line up with the compiler) — produced by reusing
 *    [KonstructionControllerImpl.copyContentToScript].
 *  - a **`workspace.json`** ([WorkspaceJson]) following the proven PoC recipe, with
 *    paths filled in for this konstruction's dir and the resolved `lib.jar`.
 *
 * Positions stay in WRAPPED-`.kt` space for Phase 2; the ±3-line csgs↔kotlin
 * translation is Phase 3 (#38) and is intentionally NOT done here.
 *
 * The synthesized workspace lives under the konstruction's script dir (sibling to
 * `content.kt`) in an `lsp/` subdir, so it is per-konstruction and never collides.
 */
class KonstructionLspWorkspace(
    private val config: Config,
    private val workspaceId: String,
    private val konstructionId: String
) {
    private val paths: PathController.Paths = PathController(config)[workspaceId, konstructionId]

    /** The synthesized workspace root opened in kotlin-lsp (`rootUri`). */
    val workspaceDir: File =
        File(paths.kotlinFile.parentFile, "lsp").also { it.mkdirs() }

    /** The wrapped Kotlin file inside the workspace, didOpen'd to the engine. */
    val wrappedFile: File = File(workspaceDir, "Konstruction.kt")

    private val workspaceJsonFile: File = File(workspaceDir, "workspace.json")

    /** `file://` URI for the workspace root. */
    val rootUri: String = fileUri(workspaceDir)

    /** `file://` URI for the wrapped document. */
    val documentUri: String = fileUri(wrappedFile)

    /**
     * (Re)write the wrapped `.kt` from the latest konstruction content and the
     * `workspace.json` pointing at this dir + the resolved `lib.jar`. Idempotent;
     * call before opening / on content change.
     */
    fun synthesize() {
        writeWrappedKt()
        writeWorkspaceJson()
    }

    /** The current wrapped-document text (post-wrapping). */
    fun wrappedText(): String = wrappedFile.readText()

    private fun writeWrappedKt() {
        val source =
            if (paths.contentFile.exists()) {
                paths.contentFile.inputStream()
            } else {
                "".byteInputStream()
            }
        // Reuse konstructor's EXACT compiler wrapping (header swap + footer) so LSP
        // positions match the compiler's view of the script.
        KonstructionControllerImpl.copyContentToScript(source, wrappedFile)
    }

    private fun writeWorkspaceJson() {
        val libJar = LibsJar.getLibsJar(config)
        val dir = workspaceDir.absolutePath
        // Built with JSON object builders (not @Serializable classes) so the backend
        // module needs no serialization compiler plugin. The shape is the PoC recipe,
        // which kotlin-lsp parses strictly: polymorphic `type` discriminators inline,
        // lowercase `compile` scope, `root_itself` inclusion.
        val model = buildJsonObject {
            putJsonArray("modules") {
                addJsonObject {
                    put("name", "konstruction")
                    put("type", "JAVA_MODULE")
                    putJsonArray("dependencies") {
                        addJsonObject { put("type", "moduleSource") }
                        addJsonObject {
                            put("type", "library")
                            put("name", LIBRARY_NAME)
                            put("scope", "compile")
                            put("isExported", false)
                        }
                    }
                    putJsonArray("contentRoots") {
                        addJsonObject {
                            put("path", dir)
                            putJsonArray("excludedUrls") { }
                            putJsonArray("excludedPatterns") { }
                            putJsonArray("sourceRoots") {
                                addJsonObject {
                                    put("path", dir)
                                    put("type", "java-source")
                                }
                            }
                        }
                    }
                    putJsonArray("facets") { }
                }
            }
            putJsonArray("libraries") {
                addJsonObject {
                    put("name", LIBRARY_NAME)
                    put("type", "java-imported")
                    put("level", "project")
                    putJsonArray("roots") {
                        addJsonObject {
                            put("path", libJar.absolutePath)
                            put("type", "CLASSES")
                            put("inclusionOptions", "root_itself")
                        }
                    }
                    putJsonArray("excludedRoots") { }
                    put("properties", JsonNull)
                }
            }
            putJsonArray("sdks") { }
            putJsonArray("kotlinSettings") { }
            putJsonArray("javaSettings") { }
        }
        workspaceJsonFile.writeText(WORKSPACE_JSON.encodeToString(model))
    }

    companion object {
        private const val LIBRARY_NAME = "kcsg-lib"

        private val WORKSPACE_JSON = Json { prettyPrint = true }

        private fun fileUri(file: File): String =
            URI("file", "", file.absoluteFile.path, null).toString()
    }
}
