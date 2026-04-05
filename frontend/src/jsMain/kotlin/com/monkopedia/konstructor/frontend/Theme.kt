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

import mui.material.styles.createTheme

val nonInvertedTheme = createTheme(
    js(
        """({
        typography: {
            useNextVariants: true,
            button: { textTransform: 'none' }
        },
        palette: {
            mode: 'light',
            secondary: { main: '#212121', light: '#9E9E9E', dark: '#212121' },
            primary: { main: '#FF5722', light: '#FFCCBC', dark: '#E64A19' },
            text: { primary: '#FFFFFF', secondary: '#FAFAFA', disabled: '#757575' }
        }
    })"""
    )
)

val invertedTheme = createTheme(
    js(
        """({
        typography: { useNextVariants: true },
        palette: {
            mode: 'dark',
            secondary: {
                main: '#EEEEEE', dark: '#cccccc', light: '#ffffff',
                contrastText: '#000000'
            },
            primary: {
                main: '#FF5722', light: '#FFCCBC', dark: '#E64A19',
                contrastText: '#212121'
            },
            text: { primary: '#FFFFFF', secondary: '#FAFAFA', disabled: '#757575' }
        }
    })"""
    )
)
