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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val KonstruktorDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF5722),
    onPrimary = Color.White,
    secondary = Color(0xFFEEEEEE),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFFAFAFA),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

val KonstruktorLightColorScheme = lightColorScheme(
    primary = Color(0xFFD84315),
    onPrimary = Color.White,
    secondary = Color(0xFF424242),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF444444),
    error = Color(0xFFB00020),
    onError = Color.White
)

fun colorSchemeForDark(isDark: Boolean): ColorScheme =
    if (isDark) KonstruktorDarkColorScheme else KonstruktorLightColorScheme
