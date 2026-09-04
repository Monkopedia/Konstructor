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
    RENDER_FAILED
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
