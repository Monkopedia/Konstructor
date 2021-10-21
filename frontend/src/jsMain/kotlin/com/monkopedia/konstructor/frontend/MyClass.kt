package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.mThemeProvider
import kotlin.math.pow
import kotlinx.browser.document
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.LinearDimension
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.width
import kotlinx.html.id
import react.dom.attrs
import react.dom.render
import styled.css
import styled.styledDiv

val x = 2.0.pow(3)

fun main() {
//    val y = rand()
    kotlinext.js.require("codemirror/lib/codemirror.css")
    kotlinext.js.require("codemirror/mode/gfm/gfm.js")
    kotlinext.js.require("codemirror/keymap/vim.js")
    kotlinext.js.require("codemirror/addon/dialog/dialog.js")
    kotlinext.js.require("codemirror/addon/dialog/dialog.css")
    render(document.getElementById("root")) {
        mThemeProvider(theme = theme) {
            child(Initializer::class) {
            }
        }
    }
}

