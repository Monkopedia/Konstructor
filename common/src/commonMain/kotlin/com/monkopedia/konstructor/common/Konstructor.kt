package com.monkopedia.konstructor.common

import com.monkopedia.ksrpc.KsMethod
import com.monkopedia.ksrpc.KsService
import com.monkopedia.ksrpc.RpcService
import kotlinx.serialization.Serializable

enum class KonstructionType {
    OBJ,
    LIB
}

@Serializable
data class Konstruction(
    val name: String,
    val id: String,
    val type: KonstructionType
)

@KsService
interface Konstructor : RpcService {
    @KsMethod("/list")
    suspend fun list(u: Unit): List<Konstruction>

    @KsMethod("/target")
    suspend fun get(id: String): KonstructionService

    @KsMethod("/create")
    suspend fun create(newItem: Konstruction): Konstruction

    @KsMethod("/delete")
    suspend fun delete(item: Konstruction): Boolean
}
