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
import com.monkopedia.lsp.Diagnostic
import com.monkopedia.lsp.Position
import com.monkopedia.lsp.Range

/**
 * The header-line position translation for LSP Phase 3 (#38).
 *
 * konstructor wraps the user's `csgs` into a `.kt` with a fixed header
 * ([KcsgScript.HEADER]) + a `}` footer ([KcsgScript.FOOTER]) before the compiler/engine
 * sees it — see
 * [com.monkopedia.konstructor.KonstructionControllerImpl.copyContentToScript]. The
 * kotlin-lsp engine therefore reports diagnostics in **wrapped-`.kt` space**: a squiggle
 * for the user's csgs line N arrives as wrapped line `N + headerLines`.
 *
 * This object translates engine ranges back to **csgs space** so squiggles land on the
 * correct line:
 *
 * ```
 * csgsLine = ktLine - headerLines
 * ```
 *
 * The header adds whole lines at column 0, so columns are unchanged. Diagnostics whose
 * line falls in the header (`ktLine < headerLines`) or the footer (beyond the user's
 * content) are **dropped** — a wrapping artifact the user can neither see nor fix must
 * never light up a wrong line. This mirrors the compiler-error handling in
 * [com.monkopedia.konstructor.tasks.CompileTask], which clamps/drops the same way.
 *
 * [headerLines] is computed from the SAME source of truth the backend already uses
 * ([KcsgScript.HEADER]`.split("\n").size`, = 3), so the offset cannot drift from the
 * compiler's view.
 */
object DiagnosticTranslation {

    /**
     * The number of header lines konstructor prepends when wrapping a csgs into a `.kt`.
     * Same computation as [com.monkopedia.konstructor.tasks.CompileTask]. Currently 3.
     */
    val headerLines: Int = KcsgScript.HEADER.split("\n").size

    /**
     * Translate a single engine [Position] (wrapped-`.kt`, zero-based) to csgs space, or
     * return `null` if it falls in the header or in/after the footer.
     *
     * @param csgsLineCount the number of lines in the user's csgs document; lines at or
     *   beyond `headerLines + csgsLineCount` are footer/synthetic and are dropped.
     */
    fun translatePosition(position: Position, csgsLineCount: Int): Position? {
        val ktLine = position.line.toInt()
        if (ktLine < headerLines) return null
        val csgsLine = ktLine - headerLines
        if (csgsLine >= csgsLineCount) return null
        // Columns are unchanged: the header adds whole lines at column 0.
        return Position(line = csgsLine.toUInt(), character = position.character)
    }

    /**
     * Translate an engine [Range] (wrapped-`.kt`) to csgs space. Returns `null` (drop the
     * diagnostic) if the range's START is in the header or in/after the footer. The END is
     * translated when it maps; otherwise it is clamped to the translated start so the
     * range stays well-formed and entirely inside the user's content.
     */
    fun translateRange(range: Range, csgsLineCount: Int): Range? {
        val start = translatePosition(range.start, csgsLineCount) ?: return null
        val end = translatePosition(range.end, csgsLineCount) ?: start
        return Range(start = start, end = end)
    }

    /**
     * Translate one engine [Diagnostic] to csgs space, or `null` if its range is in the
     * header/footer and should be dropped.
     */
    fun translateDiagnostic(diagnostic: Diagnostic, csgsLineCount: Int): Diagnostic? {
        val range = translateRange(diagnostic.range, csgsLineCount) ?: return null
        return diagnostic.copy(range = range)
    }

    /**
     * Translate a list of engine diagnostics to csgs space, dropping any that fall in the
     * header or footer.
     */
    fun translateDiagnostics(diagnostics: List<Diagnostic>, csgsLineCount: Int): List<Diagnostic> =
        diagnostics.mapNotNull { translateDiagnostic(it, csgsLineCount) }
}
