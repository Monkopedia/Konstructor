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

import kotlinx.serialization.Serializable

/**
 * Types exported by the JS bridge (see JsBridge.kt) that Playwright e2e
 * tests and any other JS caller can read from `globalThis.__konstructor.state`.
 *
 * This is the stable JS-visible surface — changing a field means changing
 * the e2e test contract.
 */

@Serializable
data class TargetSnapshot(val name: String, val color: String, val isEnabled: Boolean)

/**
 * A single compiler diagnostic surfaced in the editor, as the e2e tests see it:
 * the 1-based user-content [line] (already offset back past the wrapped `.kt`
 * header), the [message] text, and the [importance] (ERROR/WARNING/INFO).
 */
@Serializable
data class DiagnosticSnapshot(val line: Int, val message: String, val importance: String)

@Serializable
data class AppStateSnapshot(
    val ready: Boolean = false,
    val connected: Boolean = false,
    val workspaceCount: Int = -1,
    val workspaceNames: List<String> = emptyList(),
    val workspaceIds: List<String> = emptyList(),
    val selectedWorkspaceId: String? = null,
    val codePaneMode: String = "EDITOR",
    val editorTheme: String = "DRACULA",
    val keymap: String = "VIM",
    val screen: String = "loading",
    val konstructionCount: Int = 0,
    val konstructionNames: List<String> = emptyList(),
    val targets: List<TargetSnapshot> = emptyList(),
    // Editor error-footer surface (issue #33): the current diagnostics, the
    // editor's 1-based cursor line, and the footer text the editor shows for the
    // cursor's line (null when the cursor is not on a line with a message).
    val diagnostics: List<DiagnosticSnapshot> = emptyList(),
    val cursorLine: Int = 0,
    val footerError: String? = null
)
