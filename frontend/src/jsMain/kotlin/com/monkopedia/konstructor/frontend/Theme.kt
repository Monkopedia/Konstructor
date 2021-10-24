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
package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.styles.PaletteOptions
import com.ccfraser.muirwik.components.styles.ThemeOptions
import com.ccfraser.muirwik.components.styles.TypographyOptions
import com.ccfraser.muirwik.components.styles.TypographyStyle
import com.ccfraser.muirwik.components.styles.createMuiTheme
import kotlinext.js.js

private val themeOptions: ThemeOptions = (js { } as ThemeOptions).also { themeOptions ->
    themeOptions.typography = js { } as? TypographyOptions
    themeOptions.typography?.useNextVariants = true
    themeOptions.typography?.button = js {
        textTransform = "none"
    } as TypographyStyle
    // themeOptions.typography?.fontSize = 12
    themeOptions.palette = js {
        mode = "light"
        secondary = js {
            main = "#212121"
            light = "#9E9E9E"
            dark = "#212121"
        }
        primary = js {
            main = "#FF5722"
            light = "#FFCCBC"
            dark = "#E64A19"
        }
        text = js {
            main = "#FFFFFF"
            primary = "#FAFAFA"
            secondary = "#757575"
        }
    } as PaletteOptions
}

val theme = createMuiTheme(themeOptions = themeOptions)

private val invertedThemeOptions: ThemeOptions = (js { } as ThemeOptions).also { themeOptions ->
    themeOptions.typography = js { } as? TypographyOptions
    themeOptions.typography?.useNextVariants = true
    // themeOptions.typography?.fontSize = 12
    themeOptions.palette = js {
        mode = "dark"
        type = "dark"

        secondary = js {
            main = "#212121"
            light = "#9E9E9E"
            dark = "#212121"
        }
        primary = js {
            main = "#FF5722"
            light = "#FFCCBC"
            dark = "#E64A19"
        }
        text = js {
            main = "#FFFFFF"
            primary = "#FAFAFA"
            secondary = "#757575"
        }
    } as PaletteOptions
}

val invertedTheme = createMuiTheme(themeOptions = invertedThemeOptions)
