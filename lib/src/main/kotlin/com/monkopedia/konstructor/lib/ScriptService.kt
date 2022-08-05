package com.monkopedia.konstructor.lib

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
interface ScriptService : RpcService {

    @KsMethod("/initialize")
    suspend fun initialize(config: ScriptConfiguration)

    @KsMethod("/targets")
    suspend fun listTargets(onlyExports: Boolean = false): List<ScriptTargetInfo>

    @KsMethod("/build")
    suspend fun buildTarget(name: String): BuildService

    @KsMethod("/close")
    suspend fun closeService(u: Unit)
}

@KsService
interface BuildService : RpcService {
    @KsMethod("/info")
    suspend fun getInfo(u: Unit = Unit): ScriptTargetInfo

    @KsMethod("/register")
    suspend fun registerListener(listener: BuildListener)

    @KsMethod("/path")
    suspend fun getBuiltTarget(u: Unit = Unit): String

    @KsMethod("/error")
    suspend fun getErrorTrace(u: Unit = Unit): String

    @KsMethod("/time")
    suspend fun getBuildLength(u: Unit = Unit): Long
}

@KsService
interface BuildListener : RpcService {
    @KsMethod("/status")
    suspend fun onStatusUpdated(scriptTargetInfo: ScriptTargetInfo)
}
