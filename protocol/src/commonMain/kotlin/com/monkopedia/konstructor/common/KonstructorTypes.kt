package com.monkopedia.konstructor.common

import kotlinx.serialization.Serializable

@Serializable
enum class KonstructionType {
    OBJ,
    LIB
}

@Serializable
data class Konstruction(
    val name: String,
    val workspaceId: String,
    val id: String
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
    val type: KonstructionType,
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
