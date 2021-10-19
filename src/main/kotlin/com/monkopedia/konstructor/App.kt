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
import com.monkopedia.FileLogger
import com.monkopedia.Log
import com.monkopedia.StdoutLogger
import com.monkopedia.error
import com.monkopedia.imdex.Scriptorium
import com.monkopedia.ksrpc.SerializedChannel
import com.monkopedia.ksrpc.ServiceApp
import com.monkopedia.ksrpc.serializedChannel
import com.monkopedia.ksrpc.serve
import com.monkopedia.ksrpc.serveOnStd
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main(args: Array<String>) = App().main(args)

class App : ServiceApp("scriptorium") {
    private val log by option("-l", "--log", help = "Path to log to, or stdout")
        .default("/tmp/scriptorium.log")
    private val service by lazy {
        KonstructorService()
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
                    static("/") {
//                        default("index.html")
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
        return Scriptorium.serializedChannel(
            service,
            errorListener = { e ->
                Log.error(
                    StringWriter().also {
                        e.printStackTrace(PrintWriter(it))
                    }.toString()
                )
            }
        )
    }
}

