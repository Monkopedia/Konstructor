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
package com.monkopedia.konstructor.lsp

import com.monkopedia.lsp.BooleanOr
import com.monkopedia.lsp.CompletionOptions
import com.monkopedia.lsp.DefaultLanguageServer
import com.monkopedia.lsp.Diagnostic
import com.monkopedia.lsp.DiagnosticSeverity
import com.monkopedia.lsp.DidOpenTextDocumentParams
import com.monkopedia.lsp.InitializeParams
import com.monkopedia.lsp.InitializeResult
import com.monkopedia.lsp.InitializeResultServerInfo
import com.monkopedia.lsp.KsrpcLanguageClient
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.PublishDiagnosticsParams
import com.monkopedia.lsp.Range
import com.monkopedia.lsp.ServerCapabilities

/**
 * Phase 1 **stub** LSP server (no real Kotlin engine yet).
 *
 * It exists only to prove the ksrpc LSP transport pipe end-to-end:
 *  - [initialize] returns a canned [InitializeResult] advertising hover +
 *    completion support, so the editor's [LSPClient] handshake succeeds.
 *  - on [textDocumentDidOpen] it pushes exactly one canned
 *    `publishDiagnostics` (a single fake warning on line 0) back to the editor
 *    through the [client] handed to it in `KonstructionService.lsp(client)`.
 *
 * The real engine integration lands in epic #35 Phase 2; this class is the
 * minimal server that lets the round-trip be observed/asserted.
 */
class StubLanguageServer(private val client: KsrpcLanguageClient) : DefaultLanguageServer() {

    override suspend fun initialize(params: InitializeParams): InitializeResult = InitializeResult(
        capabilities = ServerCapabilities(
            completionProvider = CompletionOptions(),
            hoverProvider = BooleanOr(true)
        ),
        serverInfo = InitializeResultServerInfo(
            name = "konstructor-lsp-stub",
            version = "1"
        )
    )

    override suspend fun textDocumentDidOpen(params: DidOpenTextDocumentParams) {
        // Push a single canned diagnostic back to the editor over the reverse
        // (server→client) channel multiplexed onto the same WebSocket. This is
        // the round-trip the flag-gated Phase 1 wiring proves.
        val uri = params.textDocument.uri
        client.textDocumentPublishDiagnostics(
            PublishDiagnosticsParams(
                uri = uri,
                diagnostics = listOf(
                    Diagnostic(
                        range = Range(
                            start = Position(line = 0u, character = 0u),
                            end = Position(line = 0u, character = 1u)
                        ),
                        severity = DiagnosticSeverity.WARNING,
                        source = SOURCE,
                        message = CANNED_MESSAGE
                    )
                )
            )
        )
    }

    companion object {
        /** Source label on the canned diagnostic — also the e2e assertion hook. */
        const val SOURCE = "konstructor-lsp-stub"

        /** The canned warning message pushed on didOpen. */
        const val CANNED_MESSAGE = "LSP stub: transport pipe is connected."
    }
}
