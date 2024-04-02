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

@file:JsModule("@replit/codemirror-vim")
@file:JsNonModule
package dukat.codemirror.vim

import dukat.codemirror.search.SearchQuery
import dukat.codemirror.state.ChangeDesc
import dukat.codemirror.state.EditorSelection
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.ViewUpdate
import kotlin.js.RegExp
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

external interface CM5Range {
    var anchor: Pos
    var head: Pos
}

open external class Pos(line: Number, ch: Number) {
    open var line: Number
    open var ch: Number
}

external fun on(emitter: Any, type: String, f: Function<*>)

external fun off(emitter: Any, type: String, f: Function<*>)

external fun signal(emitter: Any, type: String, vararg args: Any)

external interface Operation {
    @nativeGetter
    operator fun get(key: String): Any?

    @nativeSetter
    operator fun set(key: String, value: Number)

    @nativeSetter
    operator fun set(key: String, value: Number?)
    var isVimOp: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var cursorActivityHandlers: Array<Function<*>>?
        get() = definedExternally
        set(value) = definedExternally
    var cursorActivity: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var lastChange: Any?
        get() = definedExternally
        set(value) = definedExternally
    var change: Any?
        get() = definedExternally
        set(value) = definedExternally
    var changeHandlers: Array<Function<*>>?
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$0` {
    var cursorCharLeft: (cm: CodeMirror) -> Unit
    var redo: (cm: CodeMirror) -> Unit
    var undo: (cm: CodeMirror) -> Unit
    var newlineAndIndent: (cm: CodeMirror) -> Unit
    var indentAuto: (cm: CodeMirror) -> Unit
}

external interface `T$1` {
    var statusbar: Element?
        get() = definedExternally
        set(value) = definedExternally
    var dialog: Element?
        get() = definedExternally
        set(value) = definedExternally
    var vimPlugin: Any?
        get() = definedExternally
        set(value) = definedExternally
    var vim: Any?
        get() = definedExternally
        set(value) = definedExternally
    var currentNotificationClose: Function<*>?
        get() = definedExternally
        set(value) = definedExternally
    var keyMap: String?
        get() = definedExternally
        set(value) = definedExternally
    var overwrite: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$2` {
    var anchor: Pos
    var head: Pos
}

external interface `T$3` {
    var text: String
    var row: Number
}

external interface `T$7` {
    var insertLeft: Boolean
}

external interface `T$8` {
    var query: RegExp
}

external interface `T$9` {
    var findNext: () -> Array<String>?
    var findPrevious: () -> Array<String>?
    var find: (back: Boolean?) -> Array<String>?
    var from: () -> Pos?
    var to: () -> Pos?
    var replace: (text: String) -> Unit
}

external interface `T$11` {
    var left: Number
    var top: Number
    var bottom: Number
}

external interface `T$12` {
    var left: Number
    var top: Number
}

external interface `T$13` {
    var left: Number
    var top: Number
    var height: Number
    var width: Number
    var clientHeight: Number
    var clientWidth: Number
}

external interface `T$14` {
    var name: dynamic /* String? | Number? | Boolean? */
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$15` {
    var cm: CodeMirror
    val to: Pos
}

external interface `T$16` {
    var changes: Array<`T$15`>
}

external interface `T$17` {
    var done: Array<`T$16`>
}

external interface `T$18` {
    var history: `T$17`
}

open external class CodeMirror(cm6: EditorView) {
    open fun openDialog(
        template: Element,
        callback: Function<*>,
        options: Any
    ): (newVal: String?) -> Unit

    open fun openNotification(template: Node, options: NotificationOptions): () -> Unit
    open var cm6: EditorView
    open var state: `T$1`
    open var `$mid`: Number
    open var curOp: Operation?
    open var options: Any
    open var _handlers: Any
    open fun on(type: String, f: Function<*>)
    open fun off(type: String, f: Function<*>)
    open fun signal(type: String, e: Any, handlers: Any = definedExternally)
    open fun indexFromPos(pos: Pos): Number
    open fun posFromIndex(offset: Number): Pos
    open fun foldCode(pos: Pos)
    open fun firstLine(): Number
    open fun lastLine(): Number
    open fun lineCount(): Number
    open fun setCursor(line: Pos, ch: Number)
    open fun setCursor(line: Number, ch: Number)
    open fun getCursor(p: String /* "head" | "anchor" | "start" | "end" */ = definedExternally): Pos
    open fun listSelections(): Array<`T$2`>
    open fun setSelections(p: Array<CM5Range>, primIndex: Number = definedExternally)
    open fun setSelection(anchor: Pos, head: Pos, options: Any = definedExternally)
    open fun getLine(row: Number): String
    open fun getLineHandle(row: Number): `T$3`
    open fun getLineNumber(handle: Any): Any
    open fun getRange(s: Pos, e: Pos): String
    open fun replaceRange(text: String, s: Pos, e: Pos)
    open fun replaceSelection(text: String)
    open fun replaceSelections(replacements: Array<String>)
    open fun getSelection(): String
    open fun getSelections(): Array<String>
    open fun somethingSelected(): Boolean
    open fun getInputField(): HTMLElement
    open fun clipPos(p: Pos): Pos
    open fun getValue(): String
    open fun setValue(text: String)
    open fun focus()
    open fun blur()
    open fun defaultTextHeight(): Number
    open fun findMatchingBracket(pos: Pos): dynamic /* `T$4` | `T$5` */
    open fun scanForBracket(
        pos: Pos,
        dir: Number /* 1 */,
        style: Any,
        config: Any
    ): dynamic /* Boolean? | `T$6`? */

    open fun scanForBracket(
        pos: Pos,
        dir: String /* "-1" */,
        style: Any,
        config: Any
    ): dynamic /* Boolean? | `T$6`? */

    open fun indentLine(line: Number, more: Boolean)
    open fun indentMore()
    open fun indentLess()
    open fun execCommand(name: String)
    open fun setBookmark(cursor: Pos, options: `T$7` = definedExternally): Marker
    open var cm6Query: SearchQuery
    open fun addOverlay(__0: `T$8`): SearchQuery?
    open fun removeOverlay(overlay: Any = definedExternally)
    open fun getSearchCursor(query: RegExp, pos: Pos): `T$9`
    open fun findPosV(
        start: Pos,
        amount: Number,
        unit: String /* "page" | "line" */,
        goalColumn: Number = definedExternally
    ): Pos /* Pos & `T$10` */

    open fun charCoords(pos: Pos, mode: String /* "div" | "local" */): `T$11`
    open fun coordsChar(coords: `T$12`, mode: String /* "div" | "local" */): Pos
    open fun getScrollInfo(): `T$13`
    open fun scrollTo(x: Number = definedExternally, y: Number = definedExternally)
    open fun scrollIntoView(pos: Pos = definedExternally, margin: Number = definedExternally)
    open fun getWrapperElement(): HTMLElement
    open fun getMode(): `T$14`
    open fun setSize(w: Number, h: Number)
    open fun refresh()
    open fun destroy()
    open var doc: `T$18`
    open var `$lastChangeEndOffset`: Number
    open fun onChange(update: ViewUpdate)
    open fun onSelectionChange()
    open fun operation(fn: Function<*>): Any
    open fun onBeforeEndOperation()
    open fun moveH(increment: Number, unit: String)
    open fun setOption(name: String, param_val: Any)
    open fun getOption(name: String): dynamic /* String? | Number? | Boolean? */
    open fun toggleOverwrite(on: Boolean)
    open fun getTokenTypeAt(pos: Pos): String /* "" | "string" | "comment" */
    open fun overWriteSelection(text: String)
    open fun isInMultiSelectMode(): Boolean
    open fun virtualSelectionMode(): Boolean
    open var virtualSelection: EditorSelection?
    open fun forEachSelection(command: Function<*>)

    companion object {
        var Pos: Any
        var StringStream: Any
        var commands: `T$0`
        var defineOption: (name: String, param_val: Any, setter: Function<*>) -> Unit
        var isWordChar: (ch: String) -> Boolean
        var keys: Any
        var keyMap: Any
        var addClass: () -> Unit
        var rmClass: () -> Unit
        var e_preventDefault: (e: Event) -> Unit
        var e_stop: (e: Event) -> Unit
        var keyName: (e: KeyboardEvent) -> String?
        var vimKey: (e: KeyboardEvent) -> String?
        var lookupKey: (key: String, map: String, handle: Function<*>) -> Unit
        var on: Any
        var off: Any
        var signal: Any
        var findMatchingTag: Any
        var findEnclosingTag: Any
    }
}

external interface NotificationOptions {
    var bottom: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var duration: Number?
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$19` {
    var from: Pos
    var to: Pos
}

external interface `T$20` {
    var open: `T$19`
    var close: `T$19`
}

external fun findMatchingTag(cm: CodeMirror, pos: Pos): `T$20`?

external fun findEnclosingTag(cm: CodeMirror, pos: Pos): `T$20`?

open external class Marker(cm: CodeMirror, offset: Number, assoc: Number) {
    open var cm: CodeMirror
    open var id: Number
    open var offset: Number?
    open var assoc: Number
    open fun clear()
    open fun find(): Pos?
    open fun update(change: ChangeDesc)
}

external var Vim: Any

external interface `T$21` {
    var status: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external fun vim(
    options: `T$21` = definedExternally
): dynamic /* `T$28` | Array<dynamic /* `T$28` | Array<Extension> */> */

external fun getCM(view: EditorView): CodeMirror?
