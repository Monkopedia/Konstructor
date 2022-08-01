package com.monkopedia.konstructor.frontend

import kotlinx.browser.document
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import mui.material.CssBaseline
import mui.material.styles.ThemeProvider
import react.FC
import react.Fragment
import react.Props
import react.create
import react.dom.client.createRoot
import react.dom.render

fun main() {
    println("Starting main")
    kotlinext.js.require("codemirror/lib/codemirror.css")
    kotlinext.js.require("codemirror/theme/darcula.css")
    kotlinext.js.require("codemirror/mode/clike/clike.js")
    kotlinext.js.require("codemirror/keymap/vim.js")
    kotlinext.js.require("codemirror/addon/dialog/dialog.js")
    kotlinext.js.require("codemirror/addon/dialog/dialog.css")
    val root = createRoot(document.getElementById("root")!!)
    root.render(
        Fragment.create {
            Base()
        }
    )
    println("Finishing main main 2")
}

val Base = FC<Props> { _ ->
    ThemeProvider {
        this.theme = invertedTheme
        CssBaseline()
        Initializer()
    }
}
