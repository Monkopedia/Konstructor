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

import com.monkopedia.kcsg.KcsgScript
import com.monkopedia.konstructor.KonstructionControllerImpl
import com.monkopedia.lsp.Diagnostic
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The maintainer-flagged round-trip test for LSP Phase 3 (#38): the header-line
 * csgs↔wrapped-`.kt` translation interacts directly with diagnostic **ranges** — an
 * off-by-N silently lights the wrong line. This is the CI-runnable guard (the real-engine
 * one lives in [com.monkopedia.konstructor.integration.BridgeLanguageServerIntegrationTest],
 * CI-skipped).
 *
 * The core round-trip: take a known kcsg error on a known csgs line, run it through the
 * SAME wrapping konstructor feeds the compiler/engine ([KonstructionControllerImpl.copyContentToScript]),
 * compute the wrapped-`.kt` line the engine would report it on, translate that range back,
 * and assert it lands on the ACTUAL csgs error line (not N±offset).
 */
class DiagnosticTranslationTest {

    /** The header that konstructor prepends; the engine reports csgs line N at N + this. */
    private val headerLines = KcsgScript.HEADER.split("\n").size

    private fun pos(line: Int, character: Int = 0): Position =
        Position(line = line.toUInt(), character = character.toUInt())

    private fun range(startLine: Int, endLine: Int = startLine): Range =
        Range(start = pos(startLine), end = pos(endLine))

    private fun diag(range: Range, message: String = "boom"): Diagnostic =
        Diagnostic(range = range, message = message)

    @Test
    fun `headerLines matches the backend source of truth`() {
        // Same computation CompileTask uses; the wrapping header is currently 3 lines.
        assertEquals(3, headerLines, "header is import + blank + main-invocation line")
        assertEquals(headerLines, DiagnosticTranslation.headerLines)
    }

    @Test
    fun `round-trip a known kcsg error lands on the exact csgs line`() {
        // A 3-line csgs with a deliberate error on csgs line index 1 (the second line).
        val csgs = """
            val ok by primitive { cube { dimensions = xyz(1.0, 1.0, 1.0) } }
            val broken = thisSymbolDoesNotExist123
            export("ok")
        """.trimIndent()
        val csgsLineCount = csgs.split("\n").size
        val csgsErrorLine = 1

        // Wrap EXACTLY as konstructor feeds the compiler/engine.
        val wrapped: File = createTempFile(suffix = ".kt").toFile()
        KonstructionControllerImpl.copyContentToScript(csgs.byteInputStream(), wrapped)
        val wrappedLines = wrapped.readLines()

        // Sanity: the engine sees the error on (csgs line + headerLines). Prove that's the
        // line the broken symbol actually lives on in the wrapped file.
        val engineLine = csgsErrorLine + headerLines
        assertTrue(
            wrappedLines[engineLine].contains("thisSymbolDoesNotExist123"),
            "wrapped line $engineLine should hold the deliberate error; " +
                "got: ${wrappedLines[engineLine]}"
        )

        // Engine reports the diagnostic on the wrapped line; translate it back.
        val engineDiag = diag(range(engineLine), message = "unresolved reference")
        val translated = DiagnosticTranslation.translateDiagnostic(engineDiag, csgsLineCount)

        assertNotNull(translated, "a user-line diagnostic must survive translation")
        assertEquals(
            csgsErrorLine.toUInt(),
            translated.range.start.line,
            "translated diagnostic must land on the exact csgs error line, not N±offset"
        )
        assertEquals(csgsErrorLine.toUInt(), translated.range.end.line)
        wrapped.delete()
    }

    @Test
    fun `user-line error subtracts the header exactly`() {
        val character = 7
        val engineDiag = diag(
            Range(
                start = pos(headerLines + 0, character),
                end = pos(headerLines + 0, character + 5)
            )
        )
        val translated = DiagnosticTranslation.translateDiagnostic(engineDiag, csgsLineCount = 3)
        assertNotNull(translated)
        // First user line -> csgs line 0; columns unchanged (header adds whole lines at 0).
        assertEquals(0u, translated.range.start.line)
        assertEquals(character.toUInt(), translated.range.start.character)
        assertEquals(0u, translated.range.end.line)
        assertEquals((character + 5).toUInt(), translated.range.end.character)
    }

    @Test
    fun `header-line error is dropped`() {
        // ktLine 0,1,2 are all header (import / blank / main-invocation) -> never shown.
        for (headerLine in 0 until headerLines) {
            assertNull(
                DiagnosticTranslation.translateDiagnostic(
                    diag(range(headerLine)),
                    csgsLineCount = 3
                ),
                "a diagnostic on header line $headerLine must be dropped"
            )
        }
    }

    @Test
    fun `footer error is dropped`() {
        // 2 csgs lines occupy wrapped lines headerLines..headerLines+1; the `}` footer is
        // at headerLines+2 and beyond -> must be dropped.
        val csgsLineCount = 2
        val footerLine = headerLines + csgsLineCount
        assertNull(
            DiagnosticTranslation.translateDiagnostic(diag(range(footerLine)), csgsLineCount),
            "a diagnostic on the footer line must be dropped"
        )
        assertNull(
            DiagnosticTranslation.translateDiagnostic(diag(range(footerLine + 5)), csgsLineCount),
            "a diagnostic beyond the footer must be dropped"
        )
    }

    @Test
    fun `boundary lines map correctly`() {
        val csgsLineCount = 4
        // Last header line -> dropped.
        assertNull(
            DiagnosticTranslation.translateDiagnostic(diag(range(headerLines - 1)), csgsLineCount)
        )
        // First user line -> csgs line 0.
        assertEquals(
            0u,
            DiagnosticTranslation.translateDiagnostic(diag(range(headerLines)), csgsLineCount)
                ?.range?.start?.line
        )
        // Last user line -> csgs line (count-1).
        val lastUserKtLine = headerLines + csgsLineCount - 1
        assertEquals(
            (csgsLineCount - 1).toUInt(),
            DiagnosticTranslation.translateDiagnostic(diag(range(lastUserKtLine)), csgsLineCount)
                ?.range?.start?.line
        )
        // One past the last user line (footer) -> dropped.
        assertNull(
            DiagnosticTranslation.translateDiagnostic(
                diag(range(headerLines + csgsLineCount)),
                csgsLineCount
            )
        )
    }

    @Test
    fun `range straddling into the footer clamps its end to the start`() {
        // A range that starts on the last user line but whose end spills into the footer
        // must stay inside the user's content (end clamped to start), never reported on a
        // synthetic line.
        val csgsLineCount = 2
        val startKt = headerLines + 1 // last user line
        val endKt = headerLines + 2 // footer
        val translated = DiagnosticTranslation.translateDiagnostic(
            diag(Range(start = pos(startKt, 3), end = pos(endKt, 1))),
            csgsLineCount
        )
        assertNotNull(translated)
        assertEquals(1u, translated.range.start.line)
        assertEquals(translated.range.start, translated.range.end, "end clamped to start")
    }

    @Test
    fun `toWrappedPosition adds the header to the line and keeps the column`() {
        // Phase 4 (#39) request direction: csgs cursor -> wrapped-.kt cursor.
        val wrapped = DiagnosticTranslation.toWrappedPosition(pos(line = 0, character = 5))
        assertEquals(headerLines.toUInt(), wrapped.line, "first csgs line maps to first user kt line")
        assertEquals(5u, wrapped.character, "columns are unchanged across the wrap")

        val wrapped2 = DiagnosticTranslation.toWrappedPosition(pos(line = 4, character = 12))
        assertEquals((4 + headerLines).toUInt(), wrapped2.line)
        assertEquals(12u, wrapped2.character)
    }

    @Test
    fun `request-then-response position round-trips back to the same csgs position`() {
        // The Phase 4 request mapping (toWrappedPosition) and the Phase 3 response mapping
        // (translatePosition) are exact inverses for any in-content cursor. An off-by-N in
        // either direction silently targets the wrong line for completion/hover.
        val csgsLineCount = 10
        for (csgsLine in 0 until csgsLineCount) {
            val original = pos(line = csgsLine, character = csgsLine + 1)
            val wrapped = DiagnosticTranslation.toWrappedPosition(original)
            // The engine reports it on (csgsLine + headerLines).
            assertEquals((csgsLine + headerLines).toUInt(), wrapped.line)
            val back = DiagnosticTranslation.translatePosition(wrapped, csgsLineCount)
            assertNotNull(back, "an in-content cursor must survive the round-trip")
            assertEquals(original.line, back.line, "round-trip must preserve the csgs line")
            assertEquals(original.character, back.character, "round-trip must preserve the column")
        }
    }

    @Test
    fun `toWrappedPosition boundary lines map to the exact user kt lines`() {
        // First user csgs line -> first wrapped user line (== headerLines).
        assertEquals(
            headerLines.toUInt(),
            DiagnosticTranslation.toWrappedPosition(pos(line = 0)).line
        )
        // Last user csgs line of a 3-line doc -> headerLines + 2.
        assertEquals(
            (headerLines + 2).toUInt(),
            DiagnosticTranslation.toWrappedPosition(pos(line = 2)).line
        )
    }

    @Test
    fun `translateDiagnostics drops header and footer entries but keeps user ones`() {
        val csgsLineCount = 2
        val all = listOf(
            diag(range(0), "header"), // dropped
            diag(range(headerLines), "user-0"), // kept -> csgs 0
            diag(range(headerLines + 1), "user-1"), // kept -> csgs 1
            diag(range(headerLines + 2), "footer") // dropped
        )
        val out = DiagnosticTranslation.translateDiagnostics(all, csgsLineCount)
        assertEquals(listOf("user-0", "user-1"), out.map { it.message })
        assertEquals(listOf(0u, 1u), out.map { it.range.start.line })
    }
}
