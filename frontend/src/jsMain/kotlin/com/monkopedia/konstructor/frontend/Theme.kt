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

import com.monkopedia.konstructor.frontend.utils.buildExt
import csstype.Color
import kotlinext.js.js
import mui.material.PaletteMode.dark
import mui.material.PaletteMode.light
import mui.material.styles.PaletteColor
import mui.material.styles.PaletteOptions
import mui.material.styles.ThemeOptions
import mui.material.styles.TypeText
import mui.material.styles.createTheme

private val themeOptions: ThemeOptions = buildExt {
    typography = buildExt {
        useNextVariants = true
        button = js {
            textTransform = "none"
        }
    }
    // themeOptions.typography?.fontSize = 12
    palette = buildExt<PaletteOptions> {
        mode = light
        secondary = buildExt<PaletteColor> {
            main = Color("#212121")
            light = Color("#9E9E9E")
            dark = Color("#212121")
        }
        primary = buildExt<PaletteColor> {
            main = Color("#FF5722")
            light = Color("#FFCCBC")
            dark = Color("#E64A19")
        }
        text = buildExt<TypeText> {
            primary = Color("#FFFFFF")
            secondary = Color("#FAFAFA")
            disabled = Color("#757575")
        }
    }
}

val theme = createTheme(options = themeOptions)

private val invertedThemeOptions: ThemeOptions = buildExt {
    typography = buildExt {
        useNextVariants = true
    }
    // themeOptions.typography?.fontSize = 12
    palette = buildExt<PaletteOptions> {
        mode = dark
//        type = "dark"

        secondary = buildExt<PaletteColor> {
            main = Color("#EEEEEE")
            dark = Color("#cccccc")
            light = Color("#ffffff")
            contrastText = Color("#000000")
        }
        primary = buildExt<PaletteColor> {
            main = Color("#FF5722")
            light = Color("#FFCCBC")
            dark = Color("#E64A19")
            contrastText = Color("#212121")
        }
        text = buildExt<TypeText> {
            primary = Color("#FFFFFF")
            secondary = Color("#FAFAFA")
            disabled = Color("#757575")
        }
    }
}

val invertedTheme = createTheme(options = invertedThemeOptions)
