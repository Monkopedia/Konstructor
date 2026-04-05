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

import com.monkopedia.hauler.attach
import com.monkopedia.hauler.debug
import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.info
import com.monkopedia.hauler.warn
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.frontend.async
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.ktor.asConnection
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.time.Duration.Companion.seconds
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull

class ServiceHolder(scope: CoroutineScope) {
    private val mutableHostname = MutableStateFlow(window.location.hostname)
    private val logger = hauler().async()
    val hostname: Flow<String> = mutableHostname

    private val mutablePort = MutableStateFlow(window.location.port.toIntOrNull())
    val port: Flow<Int?> = mutablePort

    private val mutableUseWs = MutableStateFlow(true)
    val useWs: Flow<Boolean> = mutableUseWs

    private val retryConnectCount = MutableStateFlow(0)
    private val errorListener = MutableStateFlow(
        ErrorListener {
            logger.error("Error occurred", it)
            retryConnection()
        }
    )

    private data class ConnectionState(
        val hostname: String,
        val port: Int?,
        val useWs: Boolean,
        val retryCount: Int,
        val errorListener: ErrorListener
    )

    val service = combine(
        hostname,
        port,
        useWs,
        retryConnectCount,
        errorListener,
        ::ConnectionState
    ).transformLatest { (hostname, port, useWs, retryCount, errorListener) ->
        runCatching {
            logger.info("Connecting to $useWs $hostname:$port, retrying $retryCount")
            if (retryCount < 0) return@runCatching
            val env = ksrpcEnvironment {
                this.errorListener = errorListener
            }
            @Suppress("HttpUrlsUsage")
            val url = "http://$hostname${port?.let { ":$it" } ?: ""}/konstructor"
            val conn =
                if (useWs) {
                    HttpClient { install(WebSockets) }.asWebsocketConnection(url, env)
                } else {
                    HttpClient().asConnection(url, env)
                }
            val stub = conn.defaultChannel().toStub<Konstructor, String>()
            logger.info("Connected to $useWs $hostname $port")
            emit(stub)
            resetRetryCount()
        }
    }.shareIn(scope, SharingStarted.Lazily, replay = 1)

    private fun retryConnection() {
        val value = retryConnectCount.value
        if (value < 10) {
            retryConnectCount.value = value + 1
            logger.debug("Retries set to ${value + 1}")
        } else {
            error("Too many retries")
        }
    }

    suspend fun retryConnect(): Boolean = try {
        logger.info("Trying to reconnect")
        withTimeoutOrNull(30.seconds) {
            service.take(2).collectIndexed { index, _ ->
                if (index == 0) {
                    logger.info("Got first index, attempting reconnect")
                    retryConnection()
                } else {
                    // Expected, means good to go for retry.
                    logger.info("Got second response")
                    throw NoSuchElementException()
                }
            }
            logger.debug("Done collecting??")
            false
        } ?: true
    } catch (_: NoSuchElementException) {
        logger.debug("Got no such element")
        // Expected
        true
    } catch (t: Throwable) {
        logger.warn("Got other exception", t)
        throw t
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

        fun <T> Flow<T>.tryReconnects(retries: Long = 3): Flow<T> = retry(retries) {
            RootScope.serviceHolder.retryConnect()
        }
    }
}
