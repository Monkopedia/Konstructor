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
@file:JsModule("@codemirror/language")
@file:JsNonModule

package dukat.codemirror.language

import dukat.codemirror.state.ChangeSet
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.Facet
import dukat.codemirror.state.Facet__1
import dukat.codemirror.state.Range
import dukat.codemirror.state.StateEffectType
import dukat.codemirror.state.StateField
import dukat.codemirror.view.BlockInfo
import dukat.codemirror.view.Command
import dukat.codemirror.view.Decoration
import dukat.codemirror.view.DecorationSet
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.KeyBinding
import dukat.codemirror.view.ViewUpdate
import dukat.lezer.common.NodeProp
import dukat.lezer.common.NodeType
import dukat.lezer.common.Parser
import dukat.lezer.common.SyntaxNode
import dukat.lezer.common.Tree
import dukat.lezer.common.TreeFragment
import dukat.lezer.highlight.Highlighter
import dukat.lezer.highlight.Tag
import dukat.lezer.lr.LRParser
import dukat.lezer.lr.ParserConfig
import dukat.stylemod.StyleModule
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.RegExp
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

external var languageDataProp: NodeProp<Facet<Json, Array<Json>>>

external fun defineLanguageFacet(baseData: Json = definedExternally): Facet<Json, Array<Json>>

external interface `T$0` {
    var from: Number
    var to: Number
}

open external class Language(
    data: Facet__1<Json>,
    parser: Parser,
    /* Array<`T$24` | Array<dynamic /* `T$24` | Array<Extension> */>> */
    extraExtensions: Array<Any> = definedExternally
) {
    open val data: Facet__1<Json>
    open val extension: dynamic /* `T$24` | Array<dynamic /* `T$24` | Array<Extension> */> */
    open val parser: Parser
    open fun isActiveAt(
        state: EditorState,
        pos: Number,
        side: String /* "-1" */ = definedExternally
    ): Boolean

    open fun isActiveAt(state: EditorState, pos: Number): Boolean
    open fun isActiveAt(
        state: EditorState,
        pos: Number,
        side: Number /* 0 | 1 */ = definedExternally
    ): Boolean

    open fun findRegions(state: EditorState): Array<`T$0`>
}

external interface `T$1` {
    var parser: LRParser
    var languageData: Json?
        get() = definedExternally
        set(value) = definedExternally
}

open external class LRLanguage : Language {
    override val parser: LRParser
    open fun configure(options: ParserConfig): LRLanguage

    companion object {
        fun define(spec: `T$1`): LRLanguage
    }
}

external fun syntaxTree(state: EditorState): Tree

external fun ensureSyntaxTree(
    state: EditorState,
    upto: Number,
    timeout: Number = definedExternally
): Tree?

external fun syntaxTreeAvailable(state: EditorState, upto: Number = definedExternally): Boolean

external fun forceParsing(
    view: EditorView,
    upto: Number = definedExternally,
    timeout: Number = definedExternally
): Boolean

external fun syntaxParserRunning(view: EditorView): Boolean

open external class ParseContext {
    open var parser: Any
    open val state: EditorState
    open var fragments: Array<TreeFragment>
    open var viewport: `T$0`
    open var parse: Any
    open var startParse: Any
    open var withContext: Any
    open var withoutTempSkipped: Any
    open fun skipUntilInView(from: Number, to: Number)

    companion object {
        fun getSkippingParser(until: Promise<Any> = definedExternally): Parser
        fun get(): ParseContext?
    }
}

external var language: Facet<Language, Language?>

open external class LanguageSupport {
    open val language: Language
    open val support: dynamic /* `T$24` | Array<dynamic /* `T$24` | Array<Extension> */> */
    open var extension: dynamic /* `T$24` | Array<dynamic /* `T$24` | Array<Extension> */> */

    constructor(language: Language, support: dynamic/*`T$24`*/ = definedExternally)
    constructor(language: Language)
    constructor(
        language: Language,
        support: Array<Any /* `T$24` | Array<Extension> */> = definedExternally
    )
}

external interface `T$2` {
    var name: String
    var alias: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var extensions: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var filename: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var load: (() -> Promise<LanguageSupport>)?
        get() = definedExternally
        set(value) = definedExternally
    var support: LanguageSupport?
        get() = definedExternally
        set(value) = definedExternally
}

open external class LanguageDescription {
    open val name: String
    open val alias: Array<String>
    open val extensions: Array<String>
    open val filename: RegExp?
    open var loadFunc: Any
    open var support: LanguageSupport?
    open var loading: Any
    open fun load(): Promise<LanguageSupport>

    companion object {
        fun of(spec: `T$2`): LanguageDescription
        fun matchFilename(
            descs: Array<LanguageDescription>,
            filename: String
        ): LanguageDescription?
        fun matchLanguageName(
            descs: Array<LanguageDescription>,
            name: String,
            fuzzy: Boolean = definedExternally
        ): LanguageDescription?
    }
}

typealias IndentServiceFacet = (context: IndentContext, pos: Number) -> Number?
external var indentService: Facet<IndentServiceFacet, Array<IndentServiceFacet>>

external var indentUnit: Facet<String, String>

external fun getIndentUnit(state: EditorState): Number

external fun indentString(state: EditorState, cols: Number): String

external fun getIndentation(context: IndentContext, pos: Number): Number?

external fun getIndentation(context: EditorState, pos: Number): Number?

external fun indentRange(state: EditorState, from: Number, to: Number): ChangeSet

external interface `T$3` {
    var overrideIndentation: ((pos: Number) -> Number)?
        get() = definedExternally
        set(value) = definedExternally
    var simulateBreak: Number?
        get() = definedExternally
        set(value) = definedExternally
    var simulateDoubleBreak: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$4` {
    var text: String
    var from: Number
}

open external class IndentContext(state: EditorState, options: `T$3` = definedExternally) {
    open val state: EditorState
    open var unit: Number
    open fun lineAt(pos: Number, bias: String /* "-1" */ = definedExternally): `T$4`
    open fun lineAt(pos: Number): `T$4`
    open fun lineAt(pos: Number, bias: Number /* 1 */ = definedExternally): `T$4`
    open fun textAfterPos(pos: Number, bias: String /* "-1" */ = definedExternally): String
    open fun textAfterPos(pos: Number): String
    open fun textAfterPos(pos: Number, bias: Number /* 1 */ = definedExternally): String
    open fun column(pos: Number, bias: String /* "-1" */ = definedExternally): Number
    open fun column(pos: Number): Number
    open fun column(pos: Number, bias: Number /* 1 */ = definedExternally): Number
    open fun countColumn(line: String, pos: Number = definedExternally): Number
    open fun lineIndent(pos: Number, bias: String /* "-1" */ = definedExternally): Number
    open fun lineIndent(pos: Number): Number
    open fun lineIndent(pos: Number, bias: Number /* 1 */ = definedExternally): Number
}

external var indentNodeProp: NodeProp<(context: TreeIndentContext) -> Number?>

open external class TreeIndentContext : IndentContext {
    open var base: Any
    open val pos: Number
    open val node: SyntaxNode
    open fun `continue`(): Number?
}

external interface `T$5` {
    var closing: String
    var align: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var units: Number?
        get() = definedExternally
        set(value) = definedExternally
}

external fun delimitedIndent(__0: `T$5`): (context: TreeIndentContext) -> Number

external var flatIndent: (context: TreeIndentContext) -> Number

external interface `T$6` {
    var except: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var units: Number?
        get() = definedExternally
        set(value) = definedExternally
}

external fun continuedIndent(
    __0: `T$6` = definedExternally
): (context: TreeIndentContext) -> Number

/* `T$24` | Array<dynamic /* `T$24` | Array<Extension> */> */
external fun indentOnInput(): dynamic

typealias FoldServiceFacet = (state: EditorState, lineStart: Number, lineEnd: Number) -> `T$0`?
external var foldService: Facet<FoldServiceFacet, Array<FoldServiceFacet>>

external var foldNodeProp: NodeProp<(node: SyntaxNode, state: EditorState) -> `T$0`?>

external fun foldInside(node: SyntaxNode): `T$0`?

external fun foldable(state: EditorState, lineStart: Number, lineEnd: Number): `T$0`?

external interface DocRange {
    var from: Number
    var to: Number
}

external var foldEffect: StateEffectType<DocRange>

external var unfoldEffect: StateEffectType<DocRange>

external var foldState: StateField<DecorationSet>

external fun foldedRanges(state: EditorState): DecorationSet

external var foldCode: Command

external var unfoldCode: Command

external var foldAll: Command

external var unfoldAll: Command

external var foldKeymap: Array<KeyBinding>

external interface FoldConfig {
    var placeholderDOM: ((view: EditorView, onclick: (event: Event) -> Unit) -> HTMLElement)?
        get() = definedExternally
        set(value) = definedExternally
    var placeholderText: String?
        get() = definedExternally
        set(value) = definedExternally
}

/* `T$24` | Array<dynamic /* `T$24` | Array<Extension> */> */
external fun codeFolding(config: FoldConfig = definedExternally): dynamic

external interface Handlers {
    @nativeGetter
    operator fun get(
        event: String
    ): ((view: EditorView, line: BlockInfo, event: Event) -> Boolean)?

    @nativeSetter
    operator fun set(
        event: String,
        value: (view: EditorView, line: BlockInfo, event: Event) -> Boolean
    )
}

external interface FoldGutterConfig {
    var markerDOM: ((open: Boolean) -> HTMLElement)?
        get() = definedExternally
        set(value) = definedExternally
    var openText: String?
        get() = definedExternally
        set(value) = definedExternally
    var closedText: String?
        get() = definedExternally
        set(value) = definedExternally
    var domEventHandlers: Handlers?
        get() = definedExternally
        set(value) = definedExternally
    var foldingChanged: ((update: ViewUpdate) -> Boolean)?
        get() = definedExternally
        set(value) = definedExternally
}

/* `T$24` | Array<dynamic /* `T$24` | Array<Extension> */> */
external fun foldGutter(config: FoldGutterConfig = definedExternally): dynamic

external interface `T$7` {
    var scope: dynamic /* Language? | NodeType? */
        get() = definedExternally
        set(value) = definedExternally
    var all: dynamic /* String? | StyleSpec? */
        get() = definedExternally
        set(value) = definedExternally
    var themeType: String? /* "dark" | "light" */
        get() = definedExternally
        set(value) = definedExternally
}

open external class HighlightStyle : Highlighter {
    open val module: StyleModule?
    override fun style(tags: Array<Tag>): String?
    override val scope: ((type: NodeType) -> Boolean)?

    companion object {
        fun define(specs: Array<TagStyle>, options: `T$7` = definedExternally): HighlightStyle
    }
}

external interface `T$8` {
    var fallback: Boolean
}

external fun syntaxHighlighting(
    highlighter: Highlighter,
    options: `T$8` = definedExternally
): dynamic /* `T$24` | Array<dynamic /* `T$24` | Array<Extension> */> */

external fun highlightingFor(
    state: EditorState,
    tags: Array<Tag>,
    scope: NodeType = definedExternally
): String?

external interface TagStyle {
    var tag: dynamic /* Tag | Array<Tag> */
        get() = definedExternally
        set(value) = definedExternally
    var `class`: String?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(styleProperty: String): Any?

    @nativeSetter
    operator fun set(styleProperty: String, value: Any)
}

external var defaultHighlightStyle: HighlightStyle

external interface Config {
    var afterCursor: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var brackets: String?
        get() = definedExternally
        set(value) = definedExternally
    var maxScanDistance: Number?
        get() = definedExternally
        set(value) = definedExternally
    var renderMatch: ((match: MatchResult, state: EditorState) -> Array<Range<Decoration>>)?
        get() = definedExternally
        set(value) = definedExternally
}

/* `T$24` | Array<dynamic /* `T$24` | Array<Extension> */> */
external fun bracketMatching(config: Config = definedExternally): dynamic

external interface MatchResult {
    var start: `T$0`
    var end: `T$0`?
        get() = definedExternally
        set(value) = definedExternally
    var matched: Boolean
}

external fun matchBrackets(
    state: EditorState,
    pos: Number,
    dir: String /* "-1" */,
    config: Config = definedExternally
): MatchResult?

external fun matchBrackets(state: EditorState, pos: Number, dir: String /* "-1" */): MatchResult?

external fun matchBrackets(
    state: EditorState,
    pos: Number,
    dir: Number /* 1 */,
    config: Config = definedExternally
): MatchResult?

external fun matchBrackets(state: EditorState, pos: Number, dir: Number /* 1 */): MatchResult?

open external class StringStream(string: String, tabSize: Number, indentUnit: Number) {
    open var string: String
    open var tabSize: Any
    open var indentUnit: Number
    open var pos: Number
    open var start: Number
    open var lastColumnPos: Any
    open var lastColumnValue: Any
    open fun eol(): Boolean
    open fun sol(): Boolean
    open fun peek(): String?
    open fun next(): dynamic /* String | Unit */
    open fun eat(match: String): dynamic /* String | Unit */
    open fun eat(match: RegExp): dynamic /* String | Unit */
    open fun eat(match: (ch: String) -> Boolean): dynamic /* String | Unit */
    open fun eatWhile(match: String): Boolean
    open fun eatWhile(match: RegExp): Boolean
    open fun eatWhile(match: (ch: String) -> Boolean): Boolean
    open fun eatSpace(): Boolean
    open fun skipToEnd()
    open fun skipTo(ch: String): dynamic /* Boolean | Unit */
    open fun backUp(n: Number)
    open fun column(): Number
    open fun indentation(): Number
    open fun match(
        pattern: String,
        consume: Boolean = definedExternally,
        caseInsensitive: Boolean = definedExternally
    ): dynamic /* Boolean? | RegExpMatchArray? */

    open fun match(pattern: String): dynamic /* Boolean? | RegExpMatchArray? */
    open fun match(
        pattern: String,
        consume: Boolean = definedExternally
    ): dynamic /* Boolean? | RegExpMatchArray? */

    open fun match(
        pattern: RegExp,
        consume: Boolean = definedExternally,
        caseInsensitive: Boolean = definedExternally
    ): dynamic /* Boolean? | RegExpMatchArray? */

    open fun match(pattern: RegExp): dynamic /* Boolean? | RegExpMatchArray? */
    open fun match(
        pattern: RegExp,
        consume: Boolean = definedExternally
    ): dynamic /* Boolean? | RegExpMatchArray? */

    open fun current(): String
}

external interface `T$9` {
    @nativeGetter
    operator fun get(name: String): Tag?

    @nativeSetter
    operator fun set(name: String, value: Tag)
}

external interface StreamParser<State> {
    val startState: ((indentUnit: Number) -> State)?
    fun token(stream: StringStream, state: State): String?
    val blankLine: ((state: State, indentUnit: Number) -> Unit)?
    val copyState: ((state: State) -> State)?
    val indent: ((state: State, textAfter: String, context: IndentContext) -> Number?)?
    var languageData: Json?
        get() = definedExternally
        set(value) = definedExternally
    var tokenTable: `T$9`?
        get() = definedExternally
        set(value) = definedExternally
}

open external class StreamLanguage<State> : Language {
    open var getIndent: Any

    companion object {
        fun <State> define(spec: StreamParser<State>): StreamLanguage<State>
    }
}
