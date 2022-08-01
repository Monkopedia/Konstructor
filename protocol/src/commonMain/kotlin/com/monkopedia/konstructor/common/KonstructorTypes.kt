package com.monkopedia.konstructor.common

import kotlinx.serialization.Serializable

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

enum class DirtyState {
    CLEAN,
    NEEDS_COMPILE,
    NEEDS_EXEC
}

@Serializable
data class KonstructionInfo(
    val konstruction: Konstruction,
    val type: KonstructionType,
    val dirtyState: DirtyState
)

@Serializable
data class Space(
    val id: String,
    val name: String
)
