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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Hide the startup loading overlay (spinner in index.html) once Compose has
 * completed its first composition. Call this once near the root of the app.
 * The actual DOM manipulation is done by the JS hook installed in
 * index.html; this composable just fires the callback at the right moment.
 */
@Composable
fun HideLoadingOverlayOnStartup() {
    LaunchedEffect(Unit) {
        notifyOverlayHide()
    }
}

@JsFun("() => { if (globalThis.__konstructorLoaded) globalThis.__konstructorLoaded(); }")
private external fun notifyOverlayHide()
