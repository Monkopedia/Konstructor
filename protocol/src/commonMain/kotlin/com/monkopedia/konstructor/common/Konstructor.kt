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
