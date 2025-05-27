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
package com.monkopedia.konstructor.frontend

import com.monkopedia.hauler.Box
import com.monkopedia.hauler.Flatbed
import com.monkopedia.hauler.Garage
import com.monkopedia.hauler.Hauler
import com.monkopedia.hauler.asAsync
import com.monkopedia.hauler.debug
import com.monkopedia.hauler.hauler
import com.monkopedia.hauler.info
import com.monkopedia.hauler.route
import com.monkopedia.konstructor.frontend.koin.RootScope
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mui.material.CssBaseline
import mui.material.styles.ThemeProvider
import react.FC
import react.Fragment
import react.Props
import react.create
import react.dom.client.createRoot
import web.dom.Element

// Just a global scope for logging without the warnings of GlobalScope.
val loggingScope = MainScope()

fun main() {
    console.log("Installing logging")
    val loggingJob = loggingScope.launch {
        Garage.deliveries.route(Flatbed, FlowCollector<String>::jsFormat)
    }
    loggingJob.invokeOnCompletion {
        console.log("Logging has failed, give up on everything")
    }
    val logger = hauler("Startup").asAsync(loggingScope)
    logger.debug("Starting koin")
    RootScope.init()
    logger.debug("Starting main")
    val root = createRoot(document.getElementById("root")!! as Element)
    root.render(
        Fragment.create {
            Base()
        }
    )
    window.document.onkeydown = {
        if (it.key == "1" && it.altKey) {
            logger.info("Got key event")
            it.stopPropagation()
            it.preventDefault()
        }
    }
    logger.debug("Exiting main method")
}

val Base = FC<Props> { _ ->
    ThemeProvider {
        this.theme = invertedTheme
        CssBaseline()
        Initializer()
    }
}

suspend fun FlowCollector<String>.jsFormat(box: Box) {
    val time = Instant.fromEpochMilliseconds(box.timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val prefix = "$time ${box.loggerName} - "
    box.message.split("\n").forEach {
        emit(prefix + it)
    }
}

fun Hauler.async() = asAsync(loggingScope)
