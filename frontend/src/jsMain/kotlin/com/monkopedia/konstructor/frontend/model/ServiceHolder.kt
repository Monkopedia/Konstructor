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
package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.asConnection
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn

class ServiceHolder(scope: CoroutineScope) {
    private val mutableHostname = MutableStateFlow(window.location.hostname)
    val hostname: Flow<String> = mutableHostname

    private val mutablePort = MutableStateFlow(window.location.port.toIntOrNull())
    val port: Flow<Int?> = mutablePort

    private val mutableUseWs = MutableStateFlow(true)
    val useWs: Flow<Boolean> = mutableUseWs

    val errorHandler: FlowCollector<*>.(Throwable) -> Unit = { throwable ->
        println("Error occurred: ${throwable.stackTraceToString()}")
        console.log(throwable)
        retryConnection()
    }

    private val retryConnectCount = MutableStateFlow(0)
    private val errorListener = MutableStateFlow(
        ErrorListener {
            println("Error occurred: ${it.stackTraceToString()}")
            console.log(it)
            retryConnection()
        }
    )

    val service = combine(
        hostname, port, useWs,
        retryConnectCount.filter { it >= 0 },
        errorListener
    ) { hostname, port, useWs, retryCount, errorListener ->
        val env = ksrpcEnvironment {
            this.errorListener = errorListener
        }
        val url = "http://$hostname${port?.let { ":$it" } ?: ""}/konstructor"
        val conn =
            if (useWs) HttpClient { install(WebSockets) }.asWebsocketConnection(url, env)
            else HttpClient().asConnection(url, env)
        conn.defaultChannel().toStub<Konstructor>().also {
            println("Connected to $useWs $hostname $port")
        }
    }.shareIn(scope, SharingStarted.Lazily, replay = 1)

    private fun retryConnection() {
        val value = retryConnectCount.value
        if (value < 10) {
            retryConnectCount.value = value + 1
        } else {
            error("Too many retries")
        }
    }

    suspend fun retryConnect(): Boolean {
        return try {
            service.collectIndexed { index, _ ->
                if (index == 0) {
                    retryConnection()
                } else if (index == 1) {
                    // Expected, means good to go for retry.
                    throw NoSuchElementException()
                }
            }
            false
        } catch (t: NoSuchElementException) {
            // Expected
            true
        }
    }

    fun resetRetryCount() {
        retryConnectCount.value = -1
    }

    fun setHostname(hostname: String) {
        mutableHostname.value = hostname
    }

    fun setPort(port: Int?) {
        mutablePort.value = port
    }

    fun setUseWs(useWs: Boolean) {
        mutableUseWs.value = useWs
    }

    companion object {
        fun <T> Flow<T>.tryReconnects(retries: Long = 3): Flow<T> {
            return retry(retries) { RootScope.serviceHolder.retryConnect() }
        }
    }
}
