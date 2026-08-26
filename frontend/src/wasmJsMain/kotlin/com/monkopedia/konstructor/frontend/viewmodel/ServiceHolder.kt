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
import com.monkopedia.konstructor.frontend.threejs.consoleError
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How long to wait between keep-alive pings on an established connection.
 */
private const val KEEP_ALIVE_INTERVAL_MS = 10_000L

/**
 * How long to wait before starting the next connection attempt. Deliberately a
 * flat interval — changing the retry cadence (backoff/jitter/caps) is a policy
 * question tracked separately from the resource handling here.
 */
private const val RECONNECT_DELAY_MS = 3_000L

/**
 * One attempt's worth of connection state: the stub callers talk to, plus every
 * resource that had to be allocated to produce it.
 *
 * [close] exists so the reconnect loop can release an attempt's resources
 * instead of abandoning them; a [ServiceConnection] that is dropped without
 * being closed is a leak of an [HttpClient] (which owns an engine and its own
 * coroutine scope) and of a live websocket.
 */
internal interface ServiceConnection {
    val stub: Konstructor

    suspend fun close()
}

/**
 * Establishes a [ServiceConnection]. Exists as a seam so the reconnect loop's
 * resource handling can be observed in tests without a real websocket.
 */
internal fun interface ConnectionFactory {
    suspend fun connect(): ServiceConnection
}

class ServiceHolder internal constructor(
    private val connectionFactory: ConnectionFactory,
    private val scope: CoroutineScope
) {

    constructor() : this(WebSocketConnectionFactory(), MainScope())

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
            var connection: ServiceConnection? = null
            try {
                connection = connectionFactory.connect()
                val stub = connection.stub
                stub.ping()
                _service.value = stub
                _connected.value = true
                // Keep-alive loop
                while (true) {
                    delay(KEEP_ALIVE_INTERVAL_MS)
                    try {
                        stub.ping()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        consoleError("Konstructor keep-alive ping failed: $e")
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consoleError("Konstructor connection attempt failed: $e")
                _service.value = null
                _connected.value = false
            } finally {
                // Whether the attempt failed outright or the keep-alive loop
                // dropped out, this attempt's resources are being discarded —
                // release them rather than abandoning them.
                connection?.closeQuietly()
            }
            delay(RECONNECT_DELAY_MS)
        }
    }

    private suspend fun ServiceConnection.closeQuietly() {
        try {
            withContext(NonCancellable) {
                close()
            }
        } catch (e: Throwable) {
            consoleError("Failed to close Konstructor connection: $e")
        }
    }
}

/**
 * The real factory: opens a websocket to the page's own origin.
 */
internal class WebSocketConnectionFactory : ConnectionFactory {
    override suspend fun connect(): ServiceConnection {
        val hostname = window.location.hostname
        val port = window.location.port
        val protocol = if (window.location.protocol == "https:") "wss" else "ws"
        val url = "$protocol://$hostname:$port/konstructor"
        val env = ksrpcEnvironment { }
        val client = HttpClient { install(WebSockets) }
        try {
            val conn = client.asWebsocketConnection(url, env)
            val stub = conn.defaultChannel().toStub<Konstructor, String>()
            return WebSocketServiceConnection(client, conn, stub)
        } catch (e: Throwable) {
            // The client is already allocated at this point; don't strand it
            // just because the handshake or the stub lookup blew up.
            client.close()
            throw e
        }
    }
}

private class WebSocketServiceConnection(
    private val client: HttpClient,
    private val connection: Connection<String>,
    override val stub: Konstructor
) : ServiceConnection {
    override suspend fun close() {
        try {
            connection.close()
        } finally {
            client.close()
        }
    }
}
