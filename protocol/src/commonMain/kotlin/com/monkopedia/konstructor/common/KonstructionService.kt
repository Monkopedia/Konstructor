package com.monkopedia.konstructor.common

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import io.ktor.utils.io.ByteReadChannel

@KsService
interface KonstructionService : RpcService {
    @KsMethod("/name")
    suspend fun getName(u: Unit): String

    @KsMethod("/set_name")
    suspend fun setName(name: String)

    @KsMethod("/info")
    suspend fun getInfo(u: Unit): KonstructionInfo

    @KsMethod("/fetch")
    suspend fun fetch(u: Unit): ByteReadChannel

    @KsMethod("/set")
    suspend fun set(content: ByteReadChannel)

    @KsMethod("/compile")
    suspend fun compile(u: Unit): TaskResult

    @KsMethod("/rendered")
    suspend fun rendered(u: Unit): String?

    @KsMethod("/render")
    suspend fun render(u: Unit): TaskResult
}
