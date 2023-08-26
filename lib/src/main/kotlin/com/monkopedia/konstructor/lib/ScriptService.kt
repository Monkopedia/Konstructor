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
package com.monkopedia.konstructor.lib

import com.monkopedia.hauler.Shipper
import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.channelFlow

@KsService
interface ScriptService : RpcService {

    @KsMethod("/initialize")
    suspend fun initialize(config: ScriptConfiguration)

    @KsMethod("/set_shipper")
    suspend fun setShipper(shipper: Shipper)

    @KsMethod("/hosts")
    suspend fun initializeHostServices(hostService: HostService)

    @KsMethod("/targets")
    suspend fun listTargets(onlyExports: Boolean = false): List<ScriptTargetInfo>

    @KsMethod("/build")
    suspend fun buildTarget(name: String): BuildService

    @KsMethod("/close")
    suspend fun closeService(u: Unit = Unit)
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

fun BuildService.statusFlow() = channelFlow {
    val listener = object : BuildListener {
        override suspend fun onStatusUpdated(scriptTargetInfo: ScriptTargetInfo) {
            channel.trySendBlocking(scriptTargetInfo.status)
                .onFailure {
                    System.err.println("Warning: Send failed $it")
                }
        }
    }
    registerListener(listener)
    send(getInfo().status)
    awaitClose()
}
