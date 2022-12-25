package com.monkopedia.konstructor.lib

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
interface HostService : RpcService {
    @KsMethod("/get_caching")
    suspend fun supportsCaching(u: Unit = Unit): Boolean

    @KsMethod("/check_caching")
    suspend fun checkCached(hash: String): String?

    @KsMethod("/get_stl")
    suspend fun findStl(stlName: String): String?

    @KsMethod("/store_cached")
    suspend fun storeCached(hash: String): String

    @KsMethod("/find_script")
    suspend fun findScript(csgsName: String): ScriptService
}

