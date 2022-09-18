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
package com.monkopedia.konstructor

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.ksrpc.ErrorListener
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.ServiceApp
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.asConnection
import com.monkopedia.ksrpc.channels.serve
import com.monkopedia.ksrpc.channels.serveWebsocket
import com.monkopedia.ksrpc.channels.stdInConnection
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.serialized
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.defaultResource
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.static
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

fun main(args: Array<String>) = App().main(args)

@OptIn(DelicateCoroutinesApi::class)
class App : ServiceApp("konstructor") {
    private val log by option("-l", "--log", help = "Path to log to, or stdout")
        .default("/tmp/scriptorium.log")
    private val logger = LoggerFactory.getLogger(App::class.java)
    private val config by lazy {
        Config()
    }
    private val service by lazy {
        KonstructorImpl(config)
    }

    override val env: KsrpcEnvironment = ksrpcEnvironment {
        errorListener = ErrorListener { exception ->
            logger.warn("Exception caught in ksrpc", exception)
        }
    }

    override fun run() {
        if (!stdOut && port.isEmpty() && http.isEmpty()) {
            println("No output mechanism specified, exiting")
            exitProcess(1)
        }
        for (p in port) {
            thread(start = true) {
                val socket = ServerSocket(p)
                while (true) {
                    val s = socket.accept()
                    GlobalScope.launch {
                        val context = newSingleThreadContext("$appName-socket-$p")
                        withContext(context) {
                            val connection = (s.getInputStream() to s.getOutputStream())
                                .asConnection(env)
                            connection.registerDefault(createChannel())
                        }
                        context.close()
                    }
                }
            }
        }
        runBlocking {
            for (h in http) {
                val routes = serve("/${appName.decapitalize()}", createChannel(), env)
                embeddedServer(Netty, h) {
                    install(CORS) {
                        anyHost()
                    }
                    install(WebSockets)
                    routing {
                        routes()
                        serveWebsocket("/${appName.decapitalize()}", createChannel(), env)
                        get("/model/{target...}") {
                            val target = call.parameters.getAll("target")!!.joinToString("/")
                            call.respondOutputStream {
                                service.getInputStream(target)
                                    .use { it.copyTo(this) }
                            }
                        }
                        static("/") {
                            resources("web")
                            defaultResource("web/index.html")
                        }
                    }
                }.start()
            }
            if (stdOut) {
                stdInConnection(env).registerDefault(createChannel())
            }
        }
    }

    override fun createChannel(): SerializedService {
        return service.serialized<Konstructor>(ksrpcEnvironment { })
    }
}
