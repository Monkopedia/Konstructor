package com.monkopedia.konstructor.common

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
interface Konstructor : RpcService {
    @KsMethod("/list")
    suspend fun list(u: Unit = Unit): List<Space>

    @KsMethod("/target")
    suspend fun get(id: String): Workspace

    @KsMethod("/konstruction")
    suspend fun konstruction(id: Konstruction): KonstructionService

    @KsMethod("/create")
    suspend fun create(newItem: Space): Space

    @KsMethod("/delete")
    suspend fun delete(item: Space)
}

@KsService
interface Workspace : RpcService {
    @KsMethod("/list")
    suspend fun list(u: Unit = Unit): List<Konstruction>

    @KsMethod("/create")
    suspend fun create(newItem: Konstruction): Konstruction

    @KsMethod("/delete")
    suspend fun delete(item: Konstruction)

    @KsMethod("/name")
    suspend fun getName(u: Unit = Unit): String

    @KsMethod("/set_name")
    suspend fun setName(name: String)
}
