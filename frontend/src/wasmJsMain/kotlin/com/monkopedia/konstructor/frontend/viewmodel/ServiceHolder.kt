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
package com.monkopedia.konstructor.frontend.viewmodel

import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServiceHolder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _service = MutableStateFlow<Konstructor?>(null)
    val service: StateFlow<Konstructor?> = _service.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    init {
        scope.launch {
            connect()
        }
    }

    private suspend fun connect() {
        while (true) {
            try {
                val hostname = window.location.hostname
                val port = window.location.port
                val protocol = if (window.location.protocol == "https:") "wss" else "ws"
                val url = "$protocol://$hostname:$port/konstructor"
                val env = ksrpcEnvironment { }
                val client = HttpClient { install(WebSockets) }
                val conn = client.asWebsocketConnection(url, env)
                val stub = conn.defaultChannel().toStub<Konstructor, String>()
                stub.ping()
                _service.value = stub
                _connected.value = true
                // Keep-alive loop
                while (true) {
                    delay(10_000)
                    try {
                        stub.ping()
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                _service.value = null
                _connected.value = false
            }
            delay(3_000)
        }
    }
}
