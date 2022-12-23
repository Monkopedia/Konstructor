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
    NEEDS_EXEC
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
data class KonstructionTarget(
    val name: String,
    val state: DirtyState
)

@Serializable
data class KonstructionRender(
    val konstruction: Konstruction,
    val name: String,
    val renderPath: String?
)

@Serializable
data class Space(
    val id: String,
    val name: String
)
