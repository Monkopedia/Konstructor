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
@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@codemirror/commands")
@file:JsNonModule

package dukat.codemirror.commands

import dukat.codemirror.state.AnnotationType
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.Facet
import dukat.codemirror.state.StateCommand
import dukat.codemirror.state.StateField
import dukat.codemirror.view.Command
import dukat.codemirror.view.FacetMethod
import dukat.codemirror.view.KeyBinding

external interface `T$0` {
    var open: String
    var close: String
}

external interface CommentTokens {
    var block: `T$0`?
        get() = definedExternally
        set(value) = definedExternally
    var line: String?
        get() = definedExternally
        set(value) = definedExternally
}

external var toggleComment: StateCommand

external var toggleLineComment: StateCommand

external var lineComment: StateCommand

external var lineUncomment: StateCommand

external var toggleBlockComment: StateCommand

external var blockComment: StateCommand

external var blockUncomment: StateCommand

external var toggleBlockCommentByLine: StateCommand

external var isolateHistory: AnnotationType<String /* "after" | "before" | "full" */>

external var invertedEffects: Facet<FacetMethod, Array<FacetMethod>>

external interface HistoryConfig {
    var minDepth: Number?
        get() = definedExternally
        set(value) = definedExternally
    var newGroupDelay: Number?
        get() = definedExternally
        set(value) = definedExternally
}

external fun history(config: HistoryConfig = definedExternally): dynamic
/* `T$7` | Array<dynamic /* `T$7` | Array<Extension> */> */

external var historyField: StateField<Any>

external var undo: StateCommand

external var redo: StateCommand

external var undoSelection: StateCommand

external var redoSelection: StateCommand

external var undoDepth: (state: EditorState) -> Number

external var redoDepth: (state: EditorState) -> Number

external var historyKeymap: Array<KeyBinding>

external var cursorCharLeft: Command

external var cursorCharRight: Command

external var cursorCharForward: Command

external var cursorCharBackward: Command

external var cursorGroupLeft: Command

external var cursorGroupRight: Command

external var cursorGroupForward: Command

external var cursorGroupBackward: Command

external var cursorSubwordForward: Command

external var cursorSubwordBackward: Command

external var cursorSyntaxLeft: Command

external var cursorSyntaxRight: Command

external var cursorLineUp: Command

external var cursorLineDown: Command

external var cursorPageUp: Command

external var cursorPageDown: Command

external var cursorLineBoundaryForward: Command

external var cursorLineBoundaryBackward: Command

external var cursorLineStart: Command

external var cursorLineEnd: Command

external var cursorMatchingBracket: StateCommand

external var selectMatchingBracket: StateCommand

external var selectCharLeft: Command

external var selectCharRight: Command

external var selectCharForward: Command

external var selectCharBackward: Command

external var selectGroupLeft: Command

external var selectGroupRight: Command

external var selectGroupForward: Command

external var selectGroupBackward: Command

external var selectSubwordForward: Command

external var selectSubwordBackward: Command

external var selectSyntaxLeft: Command

external var selectSyntaxRight: Command

external var selectLineUp: Command

external var selectLineDown: Command

external var selectPageUp: Command

external var selectPageDown: Command

external var selectLineBoundaryForward: Command

external var selectLineBoundaryBackward: Command

external var selectLineStart: Command

external var selectLineEnd: Command

external var cursorDocStart: StateCommand

external var cursorDocEnd: StateCommand

external var selectDocStart: StateCommand

external var selectDocEnd: StateCommand

external var selectAll: StateCommand

external var selectLine: StateCommand

external var selectParentSyntax: StateCommand

external var simplifySelection: StateCommand

external var deleteCharBackward: Command

external var deleteCharForward: Command

external var deleteGroupBackward: StateCommand

external var deleteGroupForward: StateCommand

external var deleteToLineEnd: Command

external var deleteToLineStart: Command

external var deleteTrailingWhitespace: StateCommand

external var splitLine: StateCommand

external var transposeChars: StateCommand

external var moveLineUp: StateCommand

external var moveLineDown: StateCommand

external var copyLineUp: StateCommand

external var copyLineDown: StateCommand

external var deleteLine: Command

external var insertNewline: StateCommand

external var insertNewlineAndIndent: StateCommand

external var insertBlankLine: StateCommand

external var indentSelection: StateCommand

external var indentMore: StateCommand

external var indentLess: StateCommand

external var insertTab: StateCommand

external var emacsStyleKeymap: Array<KeyBinding>

external var standardKeymap: Array<KeyBinding>

external var defaultKeymap: Array<KeyBinding>

external var indentWithTab: KeyBinding
