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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.commands.emacsStyleKeymap
import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.legacy.modes.kotlin
import com.monkopedia.kodemirror.lint.Diagnostic
import com.monkopedia.kodemirror.lint.LintConfig
import com.monkopedia.kodemirror.lint.forceLinting
import com.monkopedia.kodemirror.lint.lintGutter
import com.monkopedia.kodemirror.lint.linter
import com.monkopedia.kodemirror.lsp.LSPClient
import com.monkopedia.kodemirror.lsp.LSPClientConfig
import com.monkopedia.kodemirror.lsp.languageServerSupport
import com.monkopedia.kodemirror.materialtheme.rememberMaterialEditorTheme
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.themeamy.amy
import com.monkopedia.kodemirror.themeayulight.ayuLight
import com.monkopedia.kodemirror.themebarf.barf
import com.monkopedia.kodemirror.themebespin.bespin
import com.monkopedia.kodemirror.themebirdsofparadise.birdsOfParadise
import com.monkopedia.kodemirror.themeboysandgirls.boysAndGirls
import com.monkopedia.kodemirror.themeclouds.clouds
import com.monkopedia.kodemirror.themecobalt.cobalt
import com.monkopedia.kodemirror.themecoolglow.coolGlow
import com.monkopedia.kodemirror.themedracula.dracula
import com.monkopedia.kodemirror.themeespresso.espresso
import com.monkopedia.kodemirror.themenoctislilac.noctisLilac
import com.monkopedia.kodemirror.themerosepinedawn.rosePineDawn
import com.monkopedia.kodemirror.themesmoothy.smoothy
import com.monkopedia.kodemirror.themesolarizedlight.solarizedLight
import com.monkopedia.kodemirror.themetomorrow.tomorrow
import com.monkopedia.kodemirror.themonedark.oneDark
import com.monkopedia.kodemirror.view.KodeMirror
import com.monkopedia.kodemirror.view.editorContentStyle
import com.monkopedia.kodemirror.view.keymapOf
import com.monkopedia.kodemirror.view.lineWrapping
import com.monkopedia.kodemirror.view.onSave
import com.monkopedia.kodemirror.view.saveKeymap
import com.monkopedia.kodemirror.view.select
import com.monkopedia.konstructor.common.KonstructionType
import com.monkopedia.konstructor.frontend.reportLspDiagnostics
import com.monkopedia.konstructor.frontend.viewmodel.EditorThemeName
import com.monkopedia.konstructor.frontend.viewmodel.KeymapName
import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.UiState
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel
import konstructor.frontend.generated.resources.JetBrainsMono_Regular
import konstructor.frontend.generated.resources.Res
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.Font
import org.koin.compose.koinInject

@Composable
fun EditorPane(modifier: Modifier = Modifier) {
    val konstructionVm = koinInject<KonstructionViewModel>()
    val workspaceVm = koinInject<WorkspaceViewModel>()
    val selectedKonId by workspaceVm.selectedKonstructionId.collectAsState()
    val konstructions by workspaceVm.konstructions.collectAsState()
    val content by konstructionVm.content.collectAsState()
    val uiState by konstructionVm.state.collectAsState()
    val messages by konstructionVm.messages.collectAsState()
    val activeMessage by konstructionVm.activeMessage.collectAsState()

    // Load konstruction content when selection changes
    LaunchedEffect(selectedKonId, konstructions) {
        val k = konstructions.firstOrNull { it.id == selectedKonId }
        if (k != null) {
            konstructionVm.loadKonstruction(k)
        }
    }

    // Editor content fills the pane; the status is overlaid as a footer pinned
    // to the bottom of the pane (matching the pre-migration MessageComponent).
    Box(modifier = modifier) {
        // Only render editor when we have a selected konstruction
        val selectedKonstruction = konstructions.firstOrNull { it.id == selectedKonId }
        when {
            selectedKonId == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select a konstruction to edit", color = Color.Gray)
                }
            }

            selectedKonstruction?.type == KonstructionType.STL -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "STL file — view in 3D pane",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            else -> {
                EditorContent(
                    content = content,
                    konstructionVm = konstructionVm,
                    selectedKonId = selectedKonId!!,
                    messages = messages
                )
            }
        }

        // Status footer overlay, pinned full-width to the bottom of the pane.
        val statusModifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
        when (uiState) {
            UiState.LOADING -> StatusBar("Loading...", Color(0xFF64B5F6), statusModifier)

            UiState.SAVING -> StatusBar("Saving...", Color(0xFFFFB74D), statusModifier)

            UiState.COMPILING -> StatusBar("Compiling...", Color(0xFFFFB74D), statusModifier)

            UiState.EXECUTING -> StatusBar("Executing...", Color(0xFFFFB74D), statusModifier)

            UiState.DEFAULT -> {
                // Context-aware footer (restores pre-migration behavior): when the
                // cursor sits on a line that has a compiler message, show that
                // message's text; otherwise fall back to the message count.
                val active = activeMessage
                when {
                    active != null -> StatusBar(
                        active.message,
                        Color(0xFFEF5350),
                        statusModifier
                    )

                    messages.isNotEmpty() -> StatusBar(
                        "${messages.size} message(s)",
                        Color(0xFFEF5350),
                        statusModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorContent(
    content: String,
    konstructionVm: KonstructionViewModel,
    selectedKonId: String,
    messages: List<com.monkopedia.konstructor.common.TaskMessage>
) {
    val scope = rememberCoroutineScope()
    val settingsVm = koinInject<SettingsViewModel>()
    val themeName by settingsVm.editorTheme.collectAsState()
    val keymapName by settingsVm.keymap.collectAsState()
    val vimDisplayLineMotion by settingsVm.vimDisplayLineMotion.collectAsState()
    val lspEnabled by settingsVm.lspEnabled.collectAsState()
    // Reactive so the LSP session is (re)built once the konstruction's service
    // finishes loading, not just when selection changes.
    val currentKonstruction by konstructionVm.currentKonstruction.collectAsState()

    // Flag-gated LSP editor support (epic #35). When the flag is OFF this stays
    // null and the editor is byte-for-byte identical to before. When ON, build
    // the LSP session for the current konstruction and expose its editor
    // extension; rebuilt whenever the flag or the loaded konstruction changes.
    val lspExtension: Extension? = produceState<Extension?>(
        initialValue = null,
        key1 = lspEnabled,
        key2 = currentKonstruction
    ) {
        value = null
        if (!lspEnabled) return@produceState
        val konstruction = currentKonstruction ?: return@produceState
        val service = konstructionVm.currentService ?: return@produceState
        // Stable per-konstruction document URI for the LSP session.
        val uri = "file:///${konstruction.workspaceId}/${konstruction.id}/content.csgs"
        var lspClient: LSPClient? = null
        var server: com.monkopedia.lsp.KsrpcLanguageServer? = null
        try {
            // The editor client receives server→client pushes (publishDiagnostics)
            // over the reverse channel multiplexed onto the same WebSocket. It is
            // bound to kodemirror's routing client once the LSPClient exists.
            val editorLspClient = EditorLspClient(
                onDiagnostics = { params -> reportLspDiagnostics(uri, params.diagnostics.size) }
            )
            // Open the nested ksrpc LSP sub-service; the returned stub IS-A
            // LanguageServer, so it drops straight into kodemirror's LSPClient.
            server = service.lsp(editorLspClient)
            val client = LSPClient(
                server = server,
                config = LSPClientConfig(rootUri = "file:///${konstruction.workspaceId}")
            )
            lspClient = client
            editorLspClient.bind(client.languageClient)
            client.initialize()
            value = languageServerSupport(client, uri, "kotlin")
        } catch (_: Exception) {
            value = null
        }
        // Teardown (epic #35 Phase 5 / #40): the WebSocket is long-lived + shared across
        // konstructions, so the nested `lsp()` sub-service and the reverse client channel
        // stay registered until explicitly closed. When this LSP wiring is disposed — the
        // konstruction switches, the flag toggles off, or the editor leaves composition —
        // do a real shutdown→exit handshake and close() the server stub. Closing the stub
        // releases the `lsp()` sub-service AND triggers the backend BridgeLanguageServer's
        // close(), which releases the reverse client channel; otherwise each open→close
        // cycle would leak two sub-channels on the shared socket.
        //
        // The teardown runs on the LSPClient's own scope (a SupervisorJob deliberately
        // outliving any single editor session — see LSPClient.scope) so it survives this
        // produceState coroutine being cancelled at dispose.
        val clientForDispose = lspClient
        val serverForDispose = server
        awaitDispose {
            clientForDispose?.scope?.launch {
                runCatching { clientForDispose.shutdown() }
                runCatching { serverForDispose?.close() }
            }
        }
    }.value

    // The Vim mapping engine is a global singleton, so apply/restore the j/k →
    // gj/gk remapping imperatively (rather than as an editor extension) and tie
    // its lifetime to this effect. A null context covers normal + visual modes
    // (insert is excluded by the engine), so vertical motion follows wrapped
    // display lines. Cleanup runs when the keymap leaves Vim, the setting turns
    // off, or the editor disposes — keeping it from leaking across switches.
    DisposableEffect(keymapName, vimDisplayLineMotion) {
        val mapped = keymapName == KeymapName.VIM && vimDisplayLineMotion
        if (mapped) {
            com.monkopedia.kodemirror.vim.Vim.map("j", "gj")
            com.monkopedia.kodemirror.vim.Vim.map("k", "gk")
        }
        onDispose {
            if (mapped) {
                com.monkopedia.kodemirror.vim.Vim.unmap("j")
                com.monkopedia.kodemirror.vim.Vim.unmap("k")
            }
        }
    }

    val kotlinLang = remember { StreamLanguage.define(kotlin).extension }

    // Font must be loaded in composable context
    val monoFont = FontFamily(Font(Res.font.JetBrainsMono_Regular))
    val fontExt = editorContentStyle.of(TextStyle(fontFamily = monoFont))

    // Theme extension — Material theme needs composable context
    val materialTheme = rememberMaterialEditorTheme()
    val themeExt: Extension = when (themeName) {
        EditorThemeName.DRACULA -> dracula
        EditorThemeName.ONE_DARK -> oneDark
        EditorThemeName.AMY -> amy
        EditorThemeName.AYU_LIGHT -> ayuLight
        EditorThemeName.BARF -> barf
        EditorThemeName.BESPIN -> bespin
        EditorThemeName.BIRDS_OF_PARADISE -> birdsOfParadise
        EditorThemeName.BOYS_AND_GIRLS -> boysAndGirls
        EditorThemeName.CLOUDS -> clouds
        EditorThemeName.COBALT -> cobalt
        EditorThemeName.COOL_GLOW -> coolGlow
        EditorThemeName.ESPRESSO -> espresso
        EditorThemeName.NOCTIS_LILAC -> noctisLilac
        EditorThemeName.ROSE_PINE_DAWN -> rosePineDawn
        EditorThemeName.SMOOTHY -> smoothy
        EditorThemeName.SOLARIZED_LIGHT -> solarizedLight
        EditorThemeName.TOMORROW -> tomorrow
        EditorThemeName.MATERIAL -> materialTheme
    }

    // Keymap extension
    val keymapExt: Extension = when (keymapName) {
        KeymapName.VIM -> com.monkopedia.kodemirror.vim.vim()
        KeymapName.EMACS -> keymapOf(*emacsStyleKeymap.toTypedArray())
        KeymapName.DEFAULT -> extensionListOf() // basicSetup already includes default keymap
    }

    // Save handler via onSave facet — works with both Ctrl+S keymap and vim :w
    val saveExt = onSave.of { session ->
        konstructionVm.save(session.state.doc.toString())
    }

    // Holds the latest compiler messages so the linter source (which the lint
    // extension re-runs on doc changes) always maps the current diagnostics
    // onto up-to-date line positions. Updated below whenever messages change.
    val messagesHolder = remember(selectedKonId) { mutableStateOf<List<Diagnostic>>(emptyList()) }

    // Recreate session when konstruction, theme, keymap, or LSP support changes.
    // lspExtension is null with the flag OFF, so the extensions chain (and thus
    // the session) is unchanged from before in that case.
    val session = remember(selectedKonId, themeName, keymapName, monoFont, lspExtension) {
        // Lint extension: decorates offending lines (red error / orange warning),
        // adds gutter markers, and shows message text on hover. The source reads
        // pre-built diagnostics from the holder; setDiagnostics-driven updates
        // happen via forceLinting in the LaunchedEffect below.
        val lintSource: com.monkopedia.kodemirror.lint.LintSource = { _ ->
            messagesHolder.value
        }
        val lintExt = linter(
            source = lintSource,
            config = LintConfig(delay = 0)
        )
        var extensions = basicSetup + themeExt + fontExt + kotlinLang +
            keymapExt + saveKeymap + saveExt + lintExt + lintGutter() + lineWrapping
        // Append LSP editor support only when the flag is on and the session is
        // ready (lspExtension non-null) — keeps the flag-OFF path identical.
        if (lspExtension != null) {
            extensions = extensions + lspExtension
        }
        val config = com.monkopedia.kodemirror.state.EditorStateConfig(
            doc = content.asDoc(),
            extensions = extensions
        )
        // Report the primary cursor's 1-based line to the view model on every
        // transaction (cursor move, edit, etc.). The view model maps that line
        // onto the diagnostics to drive the context-aware footer, restoring the
        // pre-migration cursor-line error. We use the session's onUpdate callback
        // (fired for every dispatched transaction) and read the resulting state's
        // selection head, resolving it to a line via the document.
        com.monkopedia.kodemirror.view.EditorSession(
            com.monkopedia.kodemirror.state.EditorState.create(config)
        ) { tr ->
            val state = tr.state
            val line = state.doc.lineAt(state.selection.main.head).number.value
            konstructionVm.updateCursorLine(line)
        }
    }

    // Register a cursor mover so external callers (the e2e bridge) can drive the
    // cursor to a 1-based line deterministically — the editor renders to a canvas
    // with no DOM for Playwright to click. Cleared when the session is replaced.
    DisposableEffect(session, konstructionVm) {
        konstructionVm.setCursorMover { line ->
            val doc = session.state.doc
            val clamped = line.coerceIn(1, doc.lines)
            val pos = doc.line(LineNumber(clamped)).from
            session.select(pos)
        }
        onDispose { konstructionVm.setCursorMover(null) }
    }

    // Push compiler messages into the editor as diagnostics. Diagnostics are
    // rebuilt against the live document so line ranges stay correct, then the
    // linter is forced to re-run to apply them.
    LaunchedEffect(session, messages) {
        messagesHolder.value = messages.toDiagnostics(session.state.doc)
        forceLinting(session)
    }

    // When content changes externally (server load, not user save), update
    // the editor document without recreating the session — preserves undo history.
    LaunchedEffect(content) {
        val currentDoc = session.state.doc.toString()
        if (currentDoc != content && content.isNotEmpty()) {
            session.dispatch(
                com.monkopedia.kodemirror.state.TransactionSpec(
                    changes = com.monkopedia.kodemirror.state.ChangeSpec.Single(
                        from = com.monkopedia.kodemirror.state.DocPos.ZERO,
                        to = com.monkopedia.kodemirror.state.DocPos(session.state.doc.length),
                        insert = content.asInsert()
                    )
                )
            )
        }
    }

    // Editor
    Box(modifier = Modifier.fillMaxSize()) {
        KodeMirror(
            session = session,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun StatusBar(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // Opaque surface base so editor content doesn't bleed through the
            // footer overlay, with the status color as a subtle tint on top.
            .background(MaterialTheme.colorScheme.surface)
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
