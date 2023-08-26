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
@file:JsModule("@codemirror/view")
@file:JsNonModule

package dukat.codemirror.view

import dukat.codemirror.state.ChangeSet
import dukat.codemirror.state.EditorSelection
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.EditorStateConfig
import dukat.codemirror.state.Facet
import dukat.codemirror.state.Line
import dukat.codemirror.state.Range
import dukat.codemirror.state.RangeValue
import dukat.codemirror.state.SelectionRange
import dukat.codemirror.state.StateEffect
import dukat.codemirror.state.`T$4`
import dukat.codemirror.state.Transaction
import dukat.codemirror.state.TransactionSpec
import org.w3c.dom.Document
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.ShadowRoot
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent

external interface Attrs {
    @nativeGetter
    operator fun get(name: String): String?

    @nativeSetter
    operator fun set(name: String, value: String)
}

external interface MarkDecorationSpec {
    var inclusive: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var inclusiveStart: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var inclusiveEnd: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var attributes: `T$4`?
        get() = definedExternally
        set(value) = definedExternally
    var `class`: String?
        get() = definedExternally
        set(value) = definedExternally
    var tagName: String?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(other: String): Any?

    @nativeSetter
    operator fun set(other: String, value: Any)
}

external interface WidgetDecorationSpec {
    var widget: WidgetType
    var side: Number?
        get() = definedExternally
        set(value) = definedExternally
    var block: Boolean?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(other: String): Any?

    @nativeSetter
    operator fun set(other: String, value: Any)
}

external interface ReplaceDecorationSpec {
    var widget: WidgetType?
        get() = definedExternally
        set(value) = definedExternally
    var inclusive: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var inclusiveStart: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var inclusiveEnd: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var block: Boolean?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(other: String): Any?

    @nativeSetter
    operator fun set(other: String, value: Any)
}

external interface LineDecorationSpec {
    var attributes: `T$4`?
        get() = definedExternally
        set(value) = definedExternally
    var `class`: String?
        get() = definedExternally
        set(value) = definedExternally

    @nativeGetter
    operator fun get(other: String): Any?

    @nativeSetter
    operator fun set(other: String, value: Any)
}

open external class WidgetType {
    open fun toDOM(view: EditorView): HTMLElement
    open fun eq(widget: WidgetType): Boolean
    open fun updateDOM(dom: HTMLElement): Boolean
    open fun ignoreEvent(event: Event): Boolean
    open fun destroy(dom: HTMLElement)
}

external enum class BlockType {
    Text /* = 0 */,
    WidgetBefore /* = 1 */,
    WidgetAfter /* = 2 */,
    WidgetRange /* = 3 */
}

open external class Decoration(
    startSide: Number,
    endSide: Number,
    widget: WidgetType?,
    spec: Any
) : RangeValue<Decoration> {
    open val spec: Any
    open fun eq(other: Decoration): Boolean
    override fun eq(other: RangeValue<*>): Boolean

    companion object {
        fun mark(spec: MarkDecorationSpec): Decoration
        fun widget(spec: WidgetDecorationSpec): Decoration
        fun replace(spec: ReplaceDecorationSpec): Decoration
        fun line(spec: LineDecorationSpec): Decoration
        fun set(of: Range<Decoration>, sort: Boolean = definedExternally): DecorationSet
        fun set(of: Array<Range<Decoration>>, sort: Boolean = definedExternally): DecorationSet
        var none: DecorationSet
    }
}

external interface Rect {
    val left: Number
    val right: Number
    val top: Number
    val bottom: Number
}

external interface RectPartial {
    val left: Number?
        get() = definedExternally
    val right: Number?
        get() = definedExternally
    val top: Number?
        get() = definedExternally
    val bottom: Number?
        get() = definedExternally
}

external interface PluginValue {
    val update: ((update: ViewUpdate) -> Unit)?
    val destroy: (() -> Unit)?
}

external interface PluginSpec<V : PluginValue> {
    var eventHandlers: DOMEventHandlers<V>?
        get() = definedExternally
        set(value) = definedExternally
    var provide: ((plugin: ViewPlugin<V>) -> dynamic)?
        get() = definedExternally
        set(value) = definedExternally
    var decorations: ((value: V) -> DecorationSet)?
        get() = definedExternally
        set(value) = definedExternally
}

open external class ViewPlugin<V : PluginValue> {
    open var extension: dynamic /* `T$6` | Array<dynamic /* `T$6` | Array<Extension> */> */

    companion object {
        fun <V : PluginValue> define(
            create: (view: EditorView) -> V,
            spec: PluginSpec<V> = definedExternally
        ): ViewPlugin<V>

        fun <V : PluginValue> fromClass(
            cls: Any,
            spec: PluginSpec<V> = definedExternally
        ): ViewPlugin<V>
    }
}

external interface MeasureRequest<T> {
    fun read(view: EditorView): T
    val write: ((measure: T, view: EditorView) -> Unit)?
    var key: Any?
        get() = definedExternally
        set(value) = definedExternally
}

open external class ViewUpdate {
    open val view: EditorView
    open val state: EditorState
    open val transactions: Array<Transaction>
    open val changes: ChangeSet
    open val startState: EditorState
}

external interface MouseSelectionStyle {
    var get: (curEvent: MouseEvent, extend: Boolean, multiple: Boolean) -> EditorSelection
    var update: (update: ViewUpdate) -> dynamic
}

open external class BlockInfo {
    open val from: Number
    open val length: Number
    open val top: Number
    open val height: Number
    open val type: dynamic /* BlockType | Array<BlockInfo> */
}

external enum class Direction {
    LTR /* = 0 */,
    RTL /* = 1 */
}

open external class BidiSpan {
    open val from: Number
    open val to: Number
    open val level: Number
}

external interface EditorViewConfig : EditorStateConfig {
    var state: EditorState?
        get() = definedExternally
        set(value) = definedExternally
    var parent: dynamic /* Element? | DocumentFragment? */
        get() = definedExternally
        set(value) = definedExternally
    var root: dynamic /* Document? | ShadowRoot? */
        get() = definedExternally
        set(value) = definedExternally
    var dispatch: ((tr: Transaction) -> Unit)?
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$7` {
    var node: Node
    var offset: Number
}

external interface `T$8` {
    var x: Number
    var y: Number
}

external interface `T$9` {
    var y: String? /* "nearest" | "start" | "end" | "center" */
        get() = definedExternally
        set(value) = definedExternally
    var x: String? /* "nearest" | "start" | "end" | "center" */
        get() = definedExternally
        set(value) = definedExternally
    var yMargin: Number?
        get() = definedExternally
        set(value) = definedExternally
    var xMargin: Number?
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$10` {
    @nativeGetter
    operator fun get(selector: String): dynamic?

    @nativeSetter
    operator fun set(selector: String, value: dynamic)
}

external interface `T$11` {
    var dark: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

open external class EditorView(config: EditorViewConfig = definedExternally) {
    val state: EditorState
    open var _dispatch: Any
    open var _root: Any
    open val dom: HTMLElement
    open val scrollDOM: HTMLElement
    open val contentDOM: HTMLElement
    open var announceDOM: Any
    open var plugins: Any
    open var pluginMap: Any
    open var editorAttrs: Any
    open var contentAttrs: Any
    open var styleModules: Any
    open var bidiCache: Any
    open var destroyed: Any
    open fun dispatch(tr: Transaction)
    open fun dispatch(vararg specs: TransactionSpec)
    open fun update(transactions: Array<Transaction>)
    open fun setState(newState: EditorState)
    open var updatePlugins: Any
    open var updateAttrs: Any
    open var showAnnouncements: Any
    open var mountStyles: Any
    open var readMeasured: Any
    open fun <T> requestMeasure(request: MeasureRequest<T> = definedExternally)
    open fun <T : PluginValue> plugin(plugin: ViewPlugin<T>): T?
    open fun elementAtHeight(height: Number): BlockInfo
    open fun lineBlockAtHeight(height: Number): BlockInfo
    open fun lineBlockAt(pos: Number): BlockInfo
    open fun moveByChar(
        start: SelectionRange,
        forward: Boolean,
        by: (initial: String) -> (next: String) -> Boolean = definedExternally
    ): SelectionRange

    open fun moveByGroup(start: SelectionRange, forward: Boolean): SelectionRange
    open fun moveToLineBoundary(
        start: SelectionRange,
        forward: Boolean,
        includeWrap: Boolean = definedExternally
    ): SelectionRange

    open fun moveVertically(
        start: SelectionRange,
        forward: Boolean,
        distance: Number = definedExternally
    ): SelectionRange

    open fun domAtPos(pos: Number): `T$7`
    open fun posAtDOM(node: Node, offset: Number = definedExternally): Number
    open fun posAtCoords(coords: `T$8`, precise: Boolean): Number
    open fun posAtCoords(coords: `T$8`): Number?
    open fun coordsAtPos(pos: Number, side: String /* "-1" */ = definedExternally): Rect?
    open fun coordsAtPos(pos: Number): Rect?
    open fun coordsAtPos(pos: Number, side: Number /* 1 */ = definedExternally): Rect?
    open fun textDirectionAt(pos: Number): Direction
    open fun bidiSpans(line: Line): Array<BidiSpan>
    open fun focus()
    open fun setRoot(root: Document)
    open fun setRoot(root: ShadowRoot)
    open fun destroy()

    companion object {
        fun scrollIntoView(pos: Number, options: `T$9` = definedExternally): StateEffect<Any>
        fun scrollIntoView(
            pos: SelectionRange,
            options: `T$9` = definedExternally
        ): StateEffect<Any>

        var styleModule: Facet<dynamic, Array<dynamic>>
        /* `T$6` | Array<dynamic /* `T$6` | Array<Extension> */> */
        fun domEventHandlers(handlers: DOMEventHandlers<Any>): dynamic
        var inputHandler: Facet<InputHandlerFacet, Array<InputHandlerFacet>>
        var perLineTextDirection: Facet<Boolean, Boolean>
        var exceptionSink: Facet<(exception: Any) -> Unit, Array<(exception: Any) -> Unit>>
        var updateListener: Facet<(update: ViewUpdate) -> Unit, Array<(update: ViewUpdate) -> Unit>>
        var editable: Facet<Boolean, Boolean>
        var mouseSelectionStyle: Facet<MakeSelectionStyle, Array<MakeSelectionStyle>>
        var dragMovesSelection: Facet<DragMovesSelectionFacet, Array<DragMovesSelectionFacet>>
        var clickAddsSelectionRange: ClickAddsSelection
        var decorations: Facet<DecorationsFacet, Array<DecorationsFacet>>
        var atomicRanges: Facet<AtomicRangesFacet, Array<AtomicRangesFacet>>
        var scrollMargins: Facet<ScrollMarginsFacet, Array<ScrollMarginsFacet>>
        fun theme(
            spec: `T$10`,
            options: `T$11` = definedExternally
        ): Any /* `T$6` | Array<dynamic /* `T$6` | Array<Extension> */> */

        var darkTheme: Facet<Boolean, Boolean>
        /* `T$6` | Array<dynamic /* `T$6` | Array<Extension> */> */
        fun baseTheme(spec: `T$10`): dynamic
        var contentAttributes: Facet<ContentAttributesFacet, Array<ContentAttributesFacet>>
        var editorAttributes: Facet<EditorAttributesFacet, Array<EditorAttributesFacet>>
        var lineWrapping: dynamic /* `T$6` | Array<dynamic /* `T$6` | Array<Extension> */> */
        var announce: Any
        fun findFromDOM(dom: HTMLElement): EditorView?
    }
}

external interface DOMEventMap {
    @nativeGetter
    operator fun get(other: String): Any?

    @nativeSetter
    operator fun set(other: String, value: Any)
}

external interface `T$12` {
    @nativeGetter
    operator fun get(selector: String): dynamic/* StyleSpec? */

    @nativeSetter
    operator fun set(selector: String, value: dynamic/*StyleSpec*/)
}

external interface `T$13` {
    var dark: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface KeyBinding {
    var key: String?
        get() = definedExternally
        set(value) = definedExternally
    var mac: String?
        get() = definedExternally
        set(value) = definedExternally
    var win: String?
        get() = definedExternally
        set(value) = definedExternally
    var linux: String?
        get() = definedExternally
        set(value) = definedExternally
    var run: Command
    var shift: Command?
        get() = definedExternally
        set(value) = definedExternally
    var scope: String?
        get() = definedExternally
        set(value) = definedExternally
    var preventDefault: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external fun scrollPastEnd(): dynamic
