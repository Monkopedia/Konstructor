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

import com.monkopedia.lsp.DefaultLanguageClient
import com.monkopedia.lsp.LanguageClient
import com.monkopedia.lsp.PublishDiagnosticsParams

/**
 * The editor's server→client endpoint, passed as the param to
 * `KonstructionService.lsp(client)`. ksrpc multiplexes its reverse channel onto
 * the same WebSocket, so the backend can push notifications (most importantly
 * `textDocument/publishDiagnostics`) back here without a second connection.
 *
 * It forwards every server-pushed diagnostic to kodemirror's own
 * [LanguageClient] (`LSPClient.languageClient`), which resolves the file/session
 * and renders the diagnostics into the editor's `:lint` layer. It additionally
 * invokes [onDiagnostics] so callers (e.g. the e2e test bridge) can observe the
 * round-trip having completed.
 *
 * Late binding: this client must be passed to `lsp(client)` to obtain the
 * server, but kodemirror's routing [LanguageClient] only exists once the
 * resulting [com.monkopedia.kodemirror.lsp.LSPClient] is constructed from that
 * server. So [editorClient] is wired in after construction via [bind]. Until it
 * is bound, diagnostics are still reported to [onDiagnostics] but not routed to
 * the editor.
 *
 * @param onDiagnostics observation hook fired on every push, for e2e/testing.
 */
class EditorLspClient(private val onDiagnostics: (PublishDiagnosticsParams) -> Unit = {}) :
    DefaultLanguageClient() {

    private var editorClient: LanguageClient? = null

    /** Bind kodemirror's routing client once the LSPClient has been built. */
    fun bind(editorClient: LanguageClient) {
        this.editorClient = editorClient
    }

    override suspend fun textDocumentPublishDiagnostics(params: PublishDiagnosticsParams) {
        // Route into kodemirror's editor diagnostics (:lint) ...
        editorClient?.textDocumentPublishDiagnostics(params)
        // ... then notify observers (e2e bridge) that diagnostics arrived.
        onDiagnostics(params)
    }
}
