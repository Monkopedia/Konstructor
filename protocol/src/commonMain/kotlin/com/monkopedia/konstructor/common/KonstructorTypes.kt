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
package com.monkopedia.konstructor.common

import com.monkopedia.konstructor.common.KonstructionType.CSGS
import kotlinx.serialization.Serializable

@Serializable
enum class KonstructionType {
    CSGS,
    STL
}

@Serializable
data class Konstruction(
    val name: String,
    val workspaceId: String,
    val id: String,
    val type: KonstructionType = CSGS
)

/**
 * ⚠️ Adding a case is a ONE-WAY DOOR for persisted data (konstructor#140).
 *
 * This is written into every konstruction's `info.json` (both [KonstructionInfo.dirtyState]
 * and each [KonstructionTarget.state]). Appending a case keeps old files readable by new code,
 * but once new code has written the new name, code WITHOUT that case cannot decode the file:
 * `ignoreUnknownKeys` skips unknown keys, not unknown enum values, and neither field has a
 * default. [RENDER_FAILED] (#138) and [COMPILE_FAILED] (#151) are both such cases, and
 * COMPILE_FAILED is written by any failed compile, so nearly every edited konstruction has one.
 *
 * So a rollback past #138 or #151 cannot read those konstructions. On a backend that has the
 * #140 fix, each unreadable one is logged and left out of `Workspace.list()` while the rest of
 * the workspace lists normally; on an older backend the whole listing throws. Either way, to
 * get them back, roll forward again, or rewrite the state in the affected `info.json` files
 * (`"dirtyState"` and every target `"state"`) to `NEEDS_COMPILE`, which forces a fresh compile.
 *
 * Do not "fix" this by making unknown values decode as [CLEAN]: that turns a loud failure
 * into a konstruction that claims to be built when it is not.
 */
@Serializable
enum class DirtyState {
    CLEAN,
    NEEDS_COMPILE,
    NEEDS_EXEC,

    /**
     * The last render was attempted and failed: at least one target it tried to build
     * errored out at execute time.
     *
     * Distinct from [NEEDS_EXEC] on purpose. [NEEDS_EXEC] means "not built yet, go build it",
     * and the frontend auto-requests a build whenever it sees it. Reporting a failed render as
     * [NEEDS_EXEC] would therefore drive render -> fail -> info change -> re-request forever,
     * which is why the konstruction-level state used to be forced to [CLEAN] instead (#117).
     * This state says "the build ran and did not work" without asking anyone to run it again;
     * a retry is still available through the per-target states, which stay [NEEDS_EXEC].
     *
     * Appended last so the ordinals and serial names of the existing entries are unchanged.
     */
    RENDER_FAILED,

    /**
     * The last compile was attempted and failed. The compile-path twin of [RENDER_FAILED]:
     * it used to be reported as [CLEAN], which told `ScriptHostImpl.findScript` that a broken
     * import needed no compile (#139).
     *
     * Not [NEEDS_COMPILE], which would be a request to compile again rather than the result
     * of having done so. The frontend does not auto-build on this state; the compile errors
     * are delivered through the task result, and editing the content moves it back to
     * [NEEDS_COMPILE].
     *
     * Appended last, like [RENDER_FAILED], for wire and `info.json` compatibility.
     */
    COMPILE_FAILED
}

@Serializable
enum class KonstructionCallbacks {
    INFO_CHANGE,
    DIRTY_CHANGE,
    TARGET_CHANGE,
    RENDER_CHANGE,
    CONTENT_CHANGE,
    TASK_COMPLETE
}

@Serializable
data class KonstructionInfo(
    val konstruction: Konstruction,
    val dirtyState: DirtyState,
    val targets: List<KonstructionTarget> = emptyList()
)

@Serializable
data class KonstructionTarget(val name: String, val state: DirtyState)

@Serializable
data class KonstructionRender(
    val konstruction: Konstruction,
    val name: String,
    val renderPath: String?
)

@Serializable
data class Space(val id: String, val name: String)
