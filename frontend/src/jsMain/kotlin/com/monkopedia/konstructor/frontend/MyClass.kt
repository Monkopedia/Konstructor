package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.mCssBaseline
import com.ccfraser.muirwik.components.mThemeProvider
import kotlinx.browser.document
import react.dom.render
import kotlin.math.pow

val x = 2.0.pow(3)

fun main() {
//    val y = rand()
    kotlinext.js.require("codemirror/lib/codemirror.css")
    kotlinext.js.require("codemirror/theme/darcula.css")
    kotlinext.js.require("codemirror/mode/clike/clike.js")
    kotlinext.js.require("codemirror/keymap/vim.js")
    kotlinext.js.require("codemirror/addon/dialog/dialog.js")
    kotlinext.js.require("codemirror/addon/dialog/dialog.css")
    render(document.getElementById("root")) {
        mThemeProvider(theme = invertedTheme) {
            mCssBaseline()
            child(Initializer::class) {
            }
        }
    }
}
