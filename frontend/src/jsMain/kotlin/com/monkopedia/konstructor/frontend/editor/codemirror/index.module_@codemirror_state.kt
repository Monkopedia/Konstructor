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
@file:JsModule("@codemirror/state")
@file:JsNonModule

package dukat.codemirror.state

import dukat.codemirror.view.LanguageDataFacet
import dukat.codemirror.view.TransactionExtenderFacet
import dukat.codemirror.view.TransactionFilterFacet
import kotlin.js.Json

open external class Line {
    open val from: Number
    open val to: Number
    open val number: Number
    open val text: String
}

external enum class MapMode {
    Simple /* = 0 */,
    TrackDel /* = 1 */,
    TrackBefore /* = 2 */,
    TrackAfter /* = 3 */
}

open external class ChangeDesc {
    open fun iterGaps(f: (posA: Number, posB: Number, length: Number) -> Unit)
    open fun iterChangedRanges(
        f: (fromA: Number, toA: Number, fromB: Number, toB: Number) -> Unit,
        individual: Boolean = definedExternally
    )

    open fun composeDesc(other: ChangeDesc): ChangeDesc
    open fun mapDesc(other: ChangeDesc, before: Boolean = definedExternally): ChangeDesc
    open fun mapPos(pos: Number, assoc: Number = definedExternally): Number
    open fun mapPos(pos: Number): Number
    open fun mapPos(pos: Number, assoc: Number, mode: MapMode): Number?
    open fun touchesRange(
        from: Number,
        to: Number = definedExternally
    ): dynamic /* Boolean | "cover" */

    open fun toJSON(): Array<Number>

    companion object {
        fun fromJSON(json: Any): ChangeDesc
    }
}

external interface `T$5` {
    var from: Number
    var to: Number?
        get() = definedExternally
        set(value) = definedExternally
    var insert: dynamic /* String? | Text? */
        get() = definedExternally
        set(value) = definedExternally
}

open external class ChangeSet : ChangeDesc {
    open fun apply(doc: Text): Text
    override fun mapDesc(other: ChangeDesc, before: Boolean): ChangeDesc
    open fun invert(doc: Text): ChangeSet
    open fun compose(other: ChangeSet): ChangeSet
    open fun map(other: ChangeDesc, before: Boolean = definedExternally): ChangeSet
    open fun iterChanges(
        f: (fromA: Number, toA: Number, fromB: Number, toB: Number, inserted: Text) -> Unit,
        individual: Boolean = definedExternally
    )

    override fun toJSON(): Any

    companion object {
        fun of(changes: `T$5`, length: Number, lineSep: String = definedExternally): ChangeSet
        fun of(changes: ChangeSet, length: Number, lineSep: String = definedExternally): ChangeSet
        fun of(
            changes: Array<dynamic /*ChangeSpec*/>,
            length: Number,
            lineSep: String = definedExternally
        ): ChangeSet

        fun empty(length: Number): ChangeSet
        fun fromJSON(json: Any): ChangeSet
    }
}

open external class SelectionRange {
    open val from: Number
    open val to: Number
    open val anchor: Number
    open val head: Number
    open val empty: Boolean
    open var flags: Any
    open fun map(change: ChangeDesc, assoc: Number = definedExternally): SelectionRange
    open fun extend(from: Number, to: Number = definedExternally): SelectionRange
    open fun eq(other: SelectionRange): Boolean
    open fun toJSON(): Any

    companion object {
        fun fromJSON(json: Any): SelectionRange
    }
}

open external class EditorSelection {
    open val main: SelectionRange
    open val ranges: Array<SelectionRange>
    open val mainIndex: Number
    open fun map(change: ChangeDesc, assoc: Number = definedExternally): EditorSelection
    open fun eq(other: EditorSelection): Boolean
    open fun asSingle(): EditorSelection
    open fun addRange(range: SelectionRange, main: Boolean = definedExternally): EditorSelection
    open fun replaceRange(
        range: SelectionRange,
        which: Number = definedExternally
    ): EditorSelection
    open fun toJSON(): Any

    companion object {
        fun fromJSON(json: Any): EditorSelection
        fun single(anchor: Number, head: Number = definedExternally): EditorSelection
        fun create(
            ranges: Array<SelectionRange>,
            mainIndex: Number = definedExternally
        ): EditorSelection

        fun cursor(
            pos: Number,
            assoc: Number = definedExternally,
            bidiLevel: Number = definedExternally,
            goalColumn: Number = definedExternally
        ): SelectionRange

        fun range(
            anchor: Number,
            head: Number,
            goalColumn: Number = definedExternally
        ): SelectionRange
    }
}

external interface FacetConfig<Input, Output> {
    var combine: ((value: Array<Input>) -> Output)?
        get() = definedExternally
        set(value) = definedExternally
    var compare: ((a: Output, b: Output) -> Boolean)?
        get() = definedExternally
        set(value) = definedExternally
    var compareInput: ((a: Input, b: Input) -> Boolean)?
        get() = definedExternally
        set(value) = definedExternally
    var static: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    /* `T$6`? | Array<dynamic /* `T$6` | Array<Extension> */>? | ((self: Facet<Input, Output>) -> dynamic)? */
    var enables: dynamic
        get() = definedExternally
        set(value) = definedExternally
}

open external class Facet<Input, Output> {
    open var isStatic: Any
    open fun of(value: Input): dynamic /* `T$6` | Array<Extension> */
    open fun compute(
        deps: Array<Any /* Facet<Any, Any> | StateField<Any> | "doc" | "selection" */>,
        get: (state: EditorState) -> Input
    ): dynamic /* `T$6` | Array<Extension> */

    open fun computeN(
        deps: Array<Any /* Facet<Any, Any> | StateField<Any> | "doc" | "selection" */>,
        get: (state: EditorState) -> Array<Input>
    ): dynamic /* `T$6` | Array<Extension> */

    open fun <T : Input> from(field: StateField<T>): dynamic /* `T$6` | Array<Extension> */
    open fun <T> from(
        field: StateField<T>,
        get: (value: T) -> Input
    ): dynamic /* `T$6` | Array<Extension> */

    companion object {
        fun <Input, Output> define(
            config: FacetConfig<Input, Output> = definedExternally
        ): Facet<Input, Output>
    }
}

open external class Facet__1<Input> : Facet<Input, Array<Input>>

external interface StateFieldSpec<Value> {
    var create: (state: EditorState) -> Value
    var update: (value: Value, transaction: Transaction) -> Value
    var compare: ((a: Value, b: Value) -> Boolean)?
        get() = definedExternally
        set(value) = definedExternally
    var provide: ((field: StateField<Value>) -> dynamic)?
        get() = definedExternally
        set(value) = definedExternally
    var toJSON: ((value: Value, state: EditorState) -> Any)?
        get() = definedExternally
        set(value) = definedExternally
    var fromJSON: ((json: Any, state: EditorState) -> Value)?
        get() = definedExternally
        set(value) = definedExternally
}

open external class StateField<Value> {
    open var createF: Any
    open var updateF: Any
    open var compareF: Any
    open var create: Any
    open fun init(create: (state: EditorState) -> Value): dynamic /* `T$6` | Array<Extension> */

    companion object {
        fun <Value> define(config: StateFieldSpec<Value>): StateField<Value>
    }
}

external interface `T$6` {
    var extension: dynamic /* `T$6` | Array<Extension> */
        get() = definedExternally
        set(value) = definedExternally
}

external object Prec {
    var highest: (ext: dynamic /* `T$6` | Array<Extension> */) -> dynamic
    var high: (ext: dynamic /* `T$6` | Array<Extension> */) -> dynamic
    var default: (ext: dynamic /* `T$6` | Array<Extension> */) -> dynamic
    var low: (ext: dynamic /* `T$6` | Array<Extension> */) -> dynamic
    var lowest: (ext: dynamic /* `T$6` | Array<Extension> */) -> dynamic
}

open external class Compartment {
    open fun of(ext: `T$6`): dynamic /* `T$6` | Array<Extension> */
    open fun of(ext: Array<dynamic /*Extension*/>): dynamic /* `T$6` | Array<Extension> */
    open fun reconfigure(content: `T$6`): StateEffect<Any>
    open fun reconfigure(content: Array<dynamic /*Extension*/>): StateEffect<Any>
    /* `T$6`? | Array<dynamic /* `T$6` | Array<Extension> */>? */
    open fun get(state: EditorState): dynamic
}

open external class Annotation<T> {
    open val type: AnnotationType<T>
    open val value: T
    open var _isAnnotation: Any

    companion object {
        fun <T> define(): AnnotationType<T>
    }
}

open external class AnnotationType<T> {
    open fun of(value: T): Annotation<T>
}

external interface StateEffectSpec<Value> {
    var map: ((value: Value, mapping: ChangeDesc) -> Value?)?
        get() = definedExternally
        set(value) = definedExternally
}

open external class StateEffectType<Value> {
    open val map: (value: Any, mapping: ChangeDesc) -> Any?
    open fun of(value: Value): StateEffect<Value>
}

open external class StateEffect<Value> {
    open val value: Value
    open fun map(mapping: ChangeDesc): StateEffect<Value>?
    open fun <T> `is`(type: StateEffectType<T>): Boolean

    companion object {
        fun <Value> define(
            spec: StateEffectSpec<Value> = definedExternally
        ): StateEffectType<Value>
        fun mapEffects(
            effects: Array<StateEffect<Any>>,
            mapping: ChangeDesc
        ): Array<StateEffect<Any>>

        var reconfigure: StateEffectType<dynamic /* `T$6` | Array<Extension> */>
        var appendConfig: StateEffectType<dynamic /* `T$6` | Array<Extension> */>
    }
}

external interface TransactionSpec {
    var changes: dynamic /* `T$5`? | ChangeSet? | Array<ChangeSpec>? */
        get() = definedExternally
        set(value) = definedExternally
    var selection: dynamic /* EditorSelection? | `T$0`? */
        get() = definedExternally
        set(value) = definedExternally
    var effects: dynamic /* StateEffect<Any>? | Array<StateEffect<Any>>? */
        get() = definedExternally
        set(value) = definedExternally
    var annotations: dynamic /* Annotation<Any>? | Array<Annotation<Any>>? */
        get() = definedExternally
        set(value) = definedExternally
    var userEvent: String?
        get() = definedExternally
        set(value) = definedExternally
    var scrollIntoView: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var filter: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var sequential: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

open external class Transaction {
    open val startState: EditorState
    open val state: EditorState
    open val changes: ChangeSet
    open val selection: EditorSelection?
    open val effects: Array<StateEffect<Any>>
    open val scrollIntoView: Boolean
    open fun <T> annotation(type: AnnotationType<T>): T?
    open fun isUserEvent(event: String): Boolean

    companion object {
        var time: AnnotationType<Number>
        var userEvent: AnnotationType<String>
        var addToHistory: AnnotationType<Boolean>
        var remote: AnnotationType<Boolean>
    }
}

external enum class CharCategory {
    Word /* = 0 */,
    Space /* = 1 */,
    Other /* = 2 */
}

external interface EditorStateConfig {
    var doc: dynamic /* String? | Text? */
        get() = definedExternally
        set(value) = definedExternally
    var selection: dynamic /* EditorSelection? | `T$0`? */
        get() = definedExternally
        set(value) = definedExternally
    var extensions: dynamic /* `T$6`? | Array<Extension>? */
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$1` {
    var range: SelectionRange
    var changes: dynamic /* `T$5`? | ChangeSet? | Array<ChangeSpec>? */
        get() = definedExternally
        set(value) = definedExternally
    var effects: dynamic /* StateEffect<Any>? | Array<StateEffect<Any>>? */
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$2` {
    var changes: ChangeSet
    var selection: EditorSelection
    var effects: Array<StateEffect<Any>>
}

external interface `T$3` {
    @nativeGetter
    operator fun get(prop: String): StateField<Any>?

    @nativeSetter
    operator fun set(prop: String, value: StateField<Any>)
}

external interface `T$4` {
    @nativeGetter
    operator fun get(key: String): String?

    @nativeSetter
    operator fun set(key: String, value: String)
}

open external class EditorState {
    open val doc: Text
    open val selection: EditorSelection
    open fun <T> field(field: StateField<T>): T
    open fun <T> field(field: StateField<T>, require: Boolean): T?
    open fun update(vararg specs: TransactionSpec): Transaction
    open fun replaceSelection(text: String): TransactionSpec
    open fun replaceSelection(text: Text): TransactionSpec
    open fun changeByRange(f: (range: SelectionRange) -> `T$1`): `T$2`
    open fun changes(spec: `T$5` = definedExternally): ChangeSet
    open fun changes(): ChangeSet
    open fun changes(spec: ChangeSet = definedExternally): ChangeSet
    open fun changes(spec: Array<dynamic/*ChangeSpec*/> = definedExternally): ChangeSet
    open fun toText(string: String): Text
    open fun sliceDoc(from: Number = definedExternally, to: Number = definedExternally): String
    open fun <Output> facet(facet: Facet<Any, Output>): Output
    open fun toJSON(fields: `T$3` = definedExternally): Any
    open fun phrase(phrase: String, vararg insert: Any): String
    open fun <T> languageDataAt(
        name: String,
        pos: Number,
        side: String /* "-1" */ = definedExternally
    ): Array<T>

    open fun <T> languageDataAt(name: String, pos: Number): Array<T>
    open fun <T> languageDataAt(
        name: String,
        pos: Number,
        side: Number /* 0 | 1 */ = definedExternally
    ): Array<T>

    open fun charCategorizer(at: Number): (char: String) -> CharCategory
    open fun wordAt(pos: Number): SelectionRange?

    companion object {
        fun fromJSON(
            json: Any,
            config: EditorStateConfig = definedExternally,
            fields: `T$3` = definedExternally
        ): EditorState

        fun create(config: EditorStateConfig = definedExternally): EditorState
        var allowMultipleSelections: Facet<Boolean, Boolean>
        var tabSize: Facet<Number, Number>
        var lineSeparator: Facet<String, String?>
        var readOnly: Facet<Boolean, Boolean>
        var phrases: Facet<`T$4`, Array<`T$4`>>
        var languageData: Facet<LanguageDataFacet, Array<LanguageDataFacet>>
        var changeFilter: Facet<(tr: Transaction) -> dynamic, Array<(tr: Transaction) -> dynamic>>
        var transactionFilter: Facet<TransactionFilterFacet, Array<TransactionFilterFacet>>
        var transactionExtender: Facet<TransactionExtenderFacet, Array<TransactionExtenderFacet>>
    }
}

external interface `T$7` {
    var state: EditorState
    var dispatch: (transaction: Transaction) -> Unit
}

external fun <Config : Any?> combineConfig(
    configs: Array<dynamic /*Partial<Config>*/>,
    defaults: dynamic /*Partial<Config>*/,
    combine: Any = definedExternally
): Config

open external class RangeValue<T : RangeValue<T>> {
    open fun eq(other: RangeValue<*>): Boolean
    open var startSide: Number
    open var endSide: Number
    open var mapMode: MapMode
    open var point: Boolean
    open fun range(from: Number, to: Number = definedExternally): Range<T>
}

open external class Range<T : RangeValue<T>> {
    open val from: Number
    open val to: Number
    open val value: T
}

external interface RangeComparator<T : RangeValue<T>> {
    fun compareRange(from: Number, to: Number, activeA: Array<T>, activeB: Array<T>)
    fun comparePoint(from: Number, to: Number, pointA: T?, pointB: T?)
}

external interface SpanIterator<T : RangeValue<T>> {
    fun span(from: Number, to: Number, active: Array<T>, openStart: Number)
    fun point(
        from: Number,
        to: Number,
        value: T,
        active: Array<T>,
        openStart: Number,
        index: Number
    )
}

external interface RangeCursor<T> {
    var next: () -> Unit
    var value: T?
    var from: Number
    var to: Number
}

external interface RangeSetUpdate<T : RangeValue<T>> {
    var add: Array<Range<T>>?
        get() = definedExternally
        set(value) = definedExternally
    var sort: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var filter: RangeFilter<T>?
        get() = definedExternally
        set(value) = definedExternally
    var filterFrom: Number?
        get() = definedExternally
        set(value) = definedExternally
    var filterTo: Number?
        get() = definedExternally
        set(value) = definedExternally
}

open external class RangeSet<T : RangeValue<T>> {
    open fun update(updateSpec: RangeSetUpdate<T>): RangeSet<T>
    open fun map(changes: ChangeDesc): RangeSet<T>
    open fun between(from: Number, to: Number, f: (from: Number, to: Number, value: T) -> Any)
    open fun iter(from: Number = definedExternally): RangeCursor<T>

    companion object {
        fun <T : RangeValue<T>> iter(
            sets: Array<RangeSet<T>>,
            from: Number = definedExternally
        ): RangeCursor<T>

        fun <T : RangeValue<T>> compare(
            oldSets: Array<RangeSet<T>>,
            newSets: Array<RangeSet<T>>,
            textDiff: ChangeDesc,
            comparator: RangeComparator<T>,
            minPointSize: Number = definedExternally
        )

        fun <T : RangeValue<T>> eq(
            oldSets: Array<RangeSet<T>>,
            newSets: Array<RangeSet<T>>,
            from: Number = definedExternally,
            to: Number = definedExternally
        ): Boolean

        fun <T : RangeValue<T>> spans(
            sets: Array<RangeSet<T>>,
            from: Number,
            to: Number,
            iterator: SpanIterator<T>,
            minPointSize: Number = definedExternally
        ): Number

        fun <T : RangeValue<T>> of(
            ranges: Array<Range<T>>,
            sort: Boolean = definedExternally
        ): RangeSet<T>

        fun <T : RangeValue<T>> of(
            ranges: Range<T>,
            sort: Boolean = definedExternally
        ): RangeSet<T>
        var empty: RangeSet<out RangeValue<*>>
    }
}

open external class RangeSetBuilder<T : RangeValue<T>> {
    open var chunks: Any
    open var chunkPos: Any
    open var chunkStart: Any
    open var last: Any
    open var lastFrom: Any
    open var lastTo: Any
    open var from: Any
    open var to: Any
    open var value: Any
    open var maxPoint: Any
    open var setMaxPoint: Any
    open var nextLayer: Any
    open var finishChunk: Any
    open fun add(from: Number, to: Number, value: T)
    open fun finish(): RangeSet<T>
}

external fun findClusterBreak(
    str: String,
    pos: Number,
    forward: Boolean = definedExternally,
    includeExtending: Boolean = definedExternally
): Number

external fun codePointAt(str: String, pos: Number): Number

external fun fromCodePoint(code: Number): String

external fun codePointSize(code: Number): Number /* 1 | 2 */

external fun countColumn(string: String, tabSize: Number, to: Number = definedExternally): Number

external fun findColumn(
    string: String,
    col: Number,
    tabSize: Number,
    strict: Boolean = definedExternally
): Number

/**
 * The data structure for documents.
 */
external class Text {
    public operator fun iterator(): Iterator<String>

    /**
     * The length of the string.
     */
    val length: Number

    /**
     * The number of lines in the string (always >= 1).
     */
    val lines: Number

    /**
     * Get the line description around the given position.
     */
    fun lineAt(pos: Number): Line

    /**
     * Get the description for the given (1-based) line number.
     */
    fun line(n: Number): Line

    /**
     * Replace a range of the text with the given content.
     */
    fun replace(from: Number, to: Number, text: Text): Text

    /**
     * Append another document to this one.
     */
    fun append(other: Text): Text

    /**
     * Retrieve the text between the given points.
     */
    fun slice(from: Number, to: Number = definedExternally): Text

    /**
     * Retrieve a part of the document as a string
     */
    fun sliceString(
        from: Number,
        to: Number = definedExternally,
        lineSep: String = definedExternally
    ): String

    /**
     * Test whether this text is equal to another instance.
     */
    fun eq(other: Text): Boolean

    /**
     * Iterate over the text. When dir is -1, iteration happens from end to start. This will return lines and the breaks between them as separate strings.
     */
    fun iter(dir: Int = definedExternally): TextIterator

    /**
     * Iterate over a range of the text. When from > to, the iterator will run in reverse.
     */
    fun iterRange(from: Number, to: Number? = definedExternally): TextIterator

    /**
     * Return a cursor that iterates over the given range of lines, without returning the line breaks between, and yielding empty strings for empty lines.
     *
     * When from and to are given, they should be 1-based line numbers.
     */
    fun iterLines(from: Number = definedExternally, to: Number = definedExternally): TextIterator

    /**
     * Convert the document to an array of lines (which can be deserialized again via Text.of).
     */
    fun toJSON(): Array<String>

    /**
     * If this is a branch node, children will hold the Text objects that it is made up of. For leaf nodes, this holds null.
     */
    val children: Array<Text>?

    /**
     * [symbol iterator]() → Iterator<string>
     */

    companion object {
        /**
         * The empty document.
         */
        val empty: Text

        /**
         * Create a Text instance for the given array of lines.
         */
        fun of(text: Array<String>): Text
    }
}
