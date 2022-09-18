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

import com.monkopedia.ksrpc.RpcService
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService

@KsService
interface KonstructionService : RpcService {
    @KsMethod("/name")
    suspend fun getName(u: Unit = Unit): String

    @KsMethod("/set_name")
    suspend fun setName(name: String)

    @KsMethod("/info")
    suspend fun getInfo(u: Unit = Unit): KonstructionInfo

    @KsMethod("/fetch")
    suspend fun fetch(u: Unit = Unit): String

    @KsMethod("/set")
    suspend fun set(content: String)

    @KsMethod("/register")
    suspend fun register(listener: KonstructionListener): String

    @KsMethod("/unregister")
    suspend fun unregister(key: String): Boolean

    @KsMethod("/konstructed")
    suspend fun konstructed(target: String): String?

    @KsMethod("/compile")
    suspend fun compile(u: Unit = Unit): TaskResult

    @KsMethod("/request_compile")
    suspend fun requestCompile(u: Unit = Unit)

    @KsMethod("/konstruct")
    suspend fun konstruct(target: String): TaskResult

    @KsMethod("/request_konstruct")
    suspend fun requestKonstruct(target: String)

    @KsMethod("/request_konstructs")
    suspend fun requestKonstructs(targets: List<String>)
}

@KsService
interface KonstructionListener : RpcService {
    @KsMethod("/callbacks")
    suspend fun requestedCallbacks(u: Unit = Unit): List<KonstructionCallbacks>

    @KsMethod("/info_update")
    suspend fun onInfoChanged(info: KonstructionInfo)

    @KsMethod("/state_update")
    suspend fun onDirtyStateChanged(state: DirtyState)

    @KsMethod("/target_update")
    suspend fun onTargetChanged(target: KonstructionTarget)

    @KsMethod("/render_change")
    suspend fun onRenderChanged(render: KonstructionRender)

    @KsMethod("/content_change")
    suspend fun onContentChange(u: Unit = Unit)

    @KsMethod("/task_complete")
    suspend fun onTaskComplete(taskResult: TaskResult)
}
