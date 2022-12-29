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

import com.monkopedia.konstructor.frontend.koin.RootScope
import kotlinx.browser.document
import kotlinx.browser.window
import mui.material.CssBaseline
import mui.material.styles.ThemeProvider
import react.FC
import react.Fragment
import react.Props
import react.create
import react.dom.client.createRoot
import react.dom.render

fun main() {
    console.log("Starting everything")
    println("Starting koin")
    RootScope.init()
    println("Starting main")
//    kotlinext.js.require("codemirror/lib/codemirror.css")
//    kotlinext.js.require("codemirror/theme/darcula.css")
//    kotlinext.js.require("codemirror/mode/clike/clike.js")
//    kotlinext.js.require("codemirror/dist/index.js")
    val root = createRoot(document.getElementById("root")!! as dom.Element)
    root.render(
        Fragment.create {
            Base()
        }
    )
    window.document.onkeydown = {
        if (it.key == "1" && it.altKey) {
            it.stopPropagation()
            it.preventDefault()
        }
    }
    println("Finishing main main 2")
}

val Base = FC<Props> { _ ->
    ThemeProvider {
        this.theme = invertedTheme
        CssBaseline()
        Initializer()
    }
}
