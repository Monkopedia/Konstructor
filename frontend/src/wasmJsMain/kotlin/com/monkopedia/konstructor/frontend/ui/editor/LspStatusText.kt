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
package com.monkopedia.konstructor.frontend.ui.editor

import com.monkopedia.konstructor.common.LspEngineState
import com.monkopedia.konstructor.common.LspStatus

/**
 * The editor footer's warning while a respawn bound holds the kotlin-lsp engine back (#159), or
 * null when there is nothing to say. Without it a dark editor looks exactly like a broken one.
 *
 * Minutes round UP, so the last partial minute still reads "1 min" rather than "0 min".
 */
fun lspUnavailableText(status: LspStatus?): String? {
    if (status == null || status.state == LspEngineState.AVAILABLE) return null
    val minutes = ((status.retryInMillis + 59_999) / 60_000).coerceAtLeast(1)
    return "LSP unavailable (retrying in $minutes min)"
}
