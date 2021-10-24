/*
 * Copyright 2020 Jason Monk
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
import com.monkopedia.ksrpc.SerializedChannel
import com.monkopedia.ksrpc.ServiceApp
import com.monkopedia.ksrpc.serialized
import com.monkopedia.ksrpc.serve
import com.monkopedia.ksrpc.serveOnStd
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resolveResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondOutputStream
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.getOrFail
import io.ktor.util.getValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import java.io.File

fun main(args: Array<String>) = App().main(args)

class App : ServiceApp("konstructor") {
    private val log by option("-l", "--log", help = "Path to log to, or stdout")
        .default("/tmp/scriptorium.log")
    private val config by lazy {
        Config()
    }
    private val service by lazy {
        KonstructorImpl(config)
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
                            createChannel().serve(s.getInputStream(), s.getOutputStream())
                        }
                        context.close()
                    }
                }
            }
        }
        for (h in http) {
            embeddedServer(Netty, h) {
                install(CORS) {
                    anyHost()
                }
                install(StatusPages) {

                    status(HttpStatusCode.NotFound) {
                        val content = call.resolveResource("web/index.html", null)
                        if (content != null)
                            call.respond(content)
                    }
                }
                routing {
                    serve("/${appName.decapitalize()}", createChannel())
                    get("/model/{target}") {
                        val target = call.parameters.getOrFail("target")
                        call.respondOutputStream {
                            this::class.java.getResourceAsStream("/suzanne.stl").use { it.copyTo(this) }
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
            runBlocking {
                createChannel().serveOnStd()
            }
        }
    }

    override fun createChannel(): SerializedChannel {
        return service.serialized(Konstructor)
    }
}
