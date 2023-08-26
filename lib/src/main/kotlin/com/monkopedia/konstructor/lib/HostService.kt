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
