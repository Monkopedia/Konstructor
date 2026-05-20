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

import com.monkopedia.kodemirror.lint.Diagnostic
import com.monkopedia.kodemirror.lint.Severity
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.konstructor.common.MessageImportance
import com.monkopedia.konstructor.common.TaskMessage

/**
 * Converts compiler [TaskMessage]s into kodemirror [Diagnostic]s, mapping each
 * message's 1-based [TaskMessage.line] onto the full character range of that
 * line in [doc]. Messages without a line number, or whose line is outside the
 * current document, are dropped (they can't be anchored to a position).
 *
 * The resulting diagnostics drive the lint extension, which decorates the
 * offending line range (red for errors, orange/yellow for warnings) and
 * surfaces [TaskMessage.message] on hover. This mirrors the pre-migration
 * CodeMirror `markField` line decorations.
 */
fun List<TaskMessage>.toDiagnostics(doc: Text): List<Diagnostic> {
    if (isEmpty()) return emptyList()
    val lineCount = doc.lines
    return mapNotNull { message ->
        val lineNumber = message.line ?: return@mapNotNull null
        if (lineNumber < 1 || lineNumber > lineCount) return@mapNotNull null
        val line = doc.line(LineNumber(lineNumber))
        // Ensure a non-empty range even on blank lines so the decoration shows.
        val from = line.from
        val to = if (line.to > line.from) line.to else DocPos(line.from.value + 1)
        Diagnostic(
            from = from,
            to = to,
            severity = message.importance.toSeverity(),
            message = message.message,
            source = "compiler"
        )
    }
}

private fun MessageImportance.toSeverity(): Severity = when (this) {
    MessageImportance.ERROR -> Severity.ERROR
    MessageImportance.WARNING -> Severity.WARNING
    MessageImportance.INFO -> Severity.INFO
}
