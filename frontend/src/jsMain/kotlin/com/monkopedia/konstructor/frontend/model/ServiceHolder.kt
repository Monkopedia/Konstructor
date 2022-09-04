package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.connect
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.toKsrpcUri
import com.monkopedia.ksrpc.toStub
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

class ServiceHolder(scope: CoroutineScope) {
    private val mutableHostname = MutableStateFlow(window.location.hostname)
    val hostname: Flow<String> = mutableHostname

    private val mutablePort = MutableStateFlow(window.location.port.toIntOrNull())
    val port: Flow<Int?> = mutablePort

    private val mutableUseWs = MutableStateFlow(true)
    val useWs: Flow<Boolean> = mutableUseWs

    val uri = combine(hostname, port, useWs) { hostname, port, useWs ->
        buildString {
            if (useWs) append("ws")
            else append("https")
            append("://")
            append(hostname)
            if (port != null) {
                append(':')
                append(port)
            }
            append("/konstructor")
        }.toKsrpcUri()
    }

    private val retryConnectCount = MutableStateFlow(0)
    private val errorListener = MutableStateFlow(
        ErrorListener {
            println("Error occurred: ${it.stackTraceToString()}")
            console.log(it)
            retryConnectCount.value = retryConnectCount.value + 1
        }
    )

    val service = combine(
        uri,
        retryConnectCount.filter { it >= 0 },
        errorListener
    ) { uri, retryCount, errorListener ->
        uri.connect(
            ksrpcEnvironment {
                this.errorListener = errorListener
            }
        ) {
            HttpClient {
                install(WebSockets)
            }
        }.defaultChannel().toStub<Konstructor>()
    }.shareIn(scope, SharingStarted.Lazily, replay = 1)

    fun retryConnection() {
        val value = retryConnectCount.value
        if (value == 10) {
            retryConnectCount.value = value + 1
        } else {
            error("Too many retries")
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
}
