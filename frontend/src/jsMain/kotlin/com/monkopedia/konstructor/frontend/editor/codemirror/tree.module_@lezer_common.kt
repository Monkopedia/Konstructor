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
@file:JsModule("@lezer/common")
@file:JsNonModule

package dukat.lezer.common

import org.khronos.webgl.Uint16Array

external interface `T$30`<T> {
    var deserialize: ((str: String) -> T)?
        get() = definedExternally
        set(value) = definedExternally
    var perNode: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface `T$31`<T> {
    @nativeGetter
    operator fun get(selector: String): T?

    @nativeSetter
    operator fun set(selector: String, value: T)
}

open external class NodeProp<T>(config: `T$30`<T> = definedExternally) {
    open var perNode: Boolean
    open var deserialize: (str: String) -> T
    open fun add(match: `T$31`<T>): NodePropSource
    open fun add(match: (type: NodeType) -> T?): NodePropSource

    companion object {
        var closedBy: NodeProp<Array<String>>
        var openedBy: NodeProp<Array<String>>
        var group: NodeProp<Array<String>>
        var contextHash: NodeProp<Number>
        var lookAhead: NodeProp<Number>
        var mounted: NodeProp<MountedTree>
    }
}

external interface `T$32` {
    var from: Number
    var to: Number
}

open external class MountedTree(tree: Tree, overlay: Array<`T$32`>?, parser: Parser) {
    open val tree: Tree
    open val overlay: Array<`T$32`>?
    open val parser: Parser
}

external interface `T$33` {
    var id: Number
    var name: String?
        get() = definedExternally
        set(value) = definedExternally
    var props: Array<dynamic /* JsTuple<NodeProp<Any>, Any> | NodePropSource */>?
        get() = definedExternally
        set(value) = definedExternally
    var top: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var error: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var skipped: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

open external class NodeType {
    open val name: String
    open val id: Number
    open fun <T> prop(prop: NodeProp<T>): T?
    open fun `is`(name: String): Boolean
    open fun `is`(name: Number): Boolean

    companion object {
        fun define(spec: `T$33`): NodeType
        var none: NodeType
        fun <T> match(map: `T$31`<T>): (node: NodeType) -> T?
    }
}

open external class NodeSet(types: Array<NodeType>) {
    open val types: Array<NodeType>
    open fun extend(vararg props: NodePropSource): NodeSet
}

external enum class IterMode {
    ExcludeBuffers /* = 1 */,
    IncludeAnonymous /* = 2 */,
    IgnoreMounts /* = 4 */,
    IgnoreOverlays /* = 8 */
}

external interface `T$34` {
    fun enter(node: SyntaxNodeRef): dynamic /* Boolean | Unit */
    val leave: ((node: SyntaxNodeRef) -> Unit)?
    var from: Number?
        get() = definedExternally
        set(value) = definedExternally
    var to: Number?
        get() = definedExternally
        set(value) = definedExternally
    var mode: IterMode?
        get() = definedExternally
        set(value) = definedExternally
}

typealias MakeTree = (
    children: Array<dynamic /* Tree | TreeBuffer */>,
    positions: Array<Number>,
    length: Number
) -> Tree
external interface `T$35` {
    var makeTree: MakeTree?
        get() = definedExternally
        set(value) = definedExternally
}

open external class Tree(
    type: NodeType,
    children: Array<Any /* Tree | TreeBuffer */>,
    positions: Array<Number>,
    length: Number,
    props: Array<Any /* JsTuple<Any, Any> */> = definedExternally
) {
    open val type: NodeType
    open val children: Array<dynamic /* Tree | TreeBuffer */>
    open val positions: Array<Number>
    open val length: Number
    open fun cursor(mode: IterMode = definedExternally): TreeCursor
    open fun cursorAt(
        pos: Number,
        side: String /* "-1" */ = definedExternally,
        mode: IterMode = definedExternally
    ): TreeCursor

    open fun cursorAt(pos: Number): TreeCursor
    open fun cursorAt(pos: Number, side: String /* "-1" */ = definedExternally): TreeCursor
    open fun cursorAt(
        pos: Number,
        side: Number /* 0 | 1 */ = definedExternally,
        mode: IterMode = definedExternally
    ): TreeCursor

    open fun cursorAt(pos: Number, side: Number /* 0 | 1 */ = definedExternally): TreeCursor
    open fun resolve(pos: Number, side: String /* "-1" */ = definedExternally): SyntaxNode
    open fun resolve(pos: Number): SyntaxNode
    open fun resolve(pos: Number, side: Number /* 0 | 1 */ = definedExternally): SyntaxNode
    open fun resolveInner(pos: Number, side: String /* "-1" */ = definedExternally): SyntaxNode
    open fun resolveInner(pos: Number): SyntaxNode
    open fun resolveInner(pos: Number, side: Number /* 0 | 1 */ = definedExternally): SyntaxNode
    open fun iterate(spec: `T$34`)
    open fun <T> prop(prop: NodeProp<T>): T?
    open fun balance(config: `T$35` = definedExternally): Tree

    companion object {
        var empty: Tree
        fun build(data: BuildData): Tree
    }
}

external interface BuildData {
    var buffer: dynamic /* BufferCursor | Array<Number> */
        get() = definedExternally
        set(value) = definedExternally
    var nodeSet: NodeSet
    var topID: Number
    var start: Number?
        get() = definedExternally
        set(value) = definedExternally
    var bufferStart: Number?
        get() = definedExternally
        set(value) = definedExternally
    var length: Number?
        get() = definedExternally
        set(value) = definedExternally
    var maxBufferLength: Number?
        get() = definedExternally
        set(value) = definedExternally
    var reused: Array<Tree>?
        get() = definedExternally
        set(value) = definedExternally
    var minRepeatType: Number?
        get() = definedExternally
        set(value) = definedExternally
}

external interface BufferCursor {
    var pos: Number
    var id: Number
    var start: Number
    var end: Number
    var size: Number
    fun next()
    fun fork(): BufferCursor
}

open external class TreeBuffer(buffer: Uint16Array, length: Number, set: NodeSet) {
    open val buffer: Uint16Array
    open val length: Number
    open val set: NodeSet
}

external interface SyntaxNodeRef {
    val from: Number
    val to: Number
    val type: NodeType
    val name: String
    val tree: Tree?
    val node: SyntaxNode
    fun matchContext(context: Array<String>): Boolean
}

external interface SyntaxNode : SyntaxNodeRef {
    var parent: SyntaxNode?
    var firstChild: SyntaxNode?
    var lastChild: SyntaxNode?
    fun childAfter(pos: Number): SyntaxNode?
    fun childBefore(pos: Number): SyntaxNode?
    fun enter(
        pos: Number,
        side: String /* "-1" */,
        mode: IterMode = definedExternally
    ): SyntaxNode?
    fun enter(pos: Number, side: String /* "-1" */): SyntaxNode?
    fun enter(
        pos: Number,
        side: Number /* 0 | 1 */,
        mode: IterMode = definedExternally
    ): SyntaxNode?

    fun enter(pos: Number, side: Number /* 0 | 1 */): SyntaxNode?
    var nextSibling: SyntaxNode?
    var prevSibling: SyntaxNode?
    fun cursor(mode: IterMode = definedExternally): TreeCursor
    fun resolve(pos: Number, side: String /* "-1" */ = definedExternally): SyntaxNode
    fun resolve(pos: Number): SyntaxNode
    fun resolve(pos: Number, side: Number /* 0 | 1 */ = definedExternally): SyntaxNode
    fun resolveInner(pos: Number, side: String /* "-1" */ = definedExternally): SyntaxNode
    fun resolveInner(pos: Number): SyntaxNode
    fun resolveInner(pos: Number, side: Number /* 0 | 1 */ = definedExternally): SyntaxNode
    fun enterUnfinishedNodesBefore(pos: Number): SyntaxNode
    fun toTree(): Tree
    fun getChild(
        type: String,
        before: String? = definedExternally,
        after: String? = definedExternally
    ): SyntaxNode?

    fun getChild(type: String): SyntaxNode?
    fun getChild(type: String, before: String? = definedExternally): SyntaxNode?
    fun getChild(
        type: String,
        before: String? = definedExternally,
        after: Number? = definedExternally
    ): SyntaxNode?

    fun getChild(
        type: String,
        before: Number? = definedExternally,
        after: String? = definedExternally
    ): SyntaxNode?

    fun getChild(type: String, before: Number? = definedExternally): SyntaxNode?
    fun getChild(
        type: String,
        before: Number? = definedExternally,
        after: Number? = definedExternally
    ): SyntaxNode?

    fun getChild(
        type: Number,
        before: String? = definedExternally,
        after: String? = definedExternally
    ): SyntaxNode?

    fun getChild(type: Number): SyntaxNode?
    fun getChild(type: Number, before: String? = definedExternally): SyntaxNode?
    fun getChild(
        type: Number,
        before: String? = definedExternally,
        after: Number? = definedExternally
    ): SyntaxNode?

    fun getChild(
        type: Number,
        before: Number? = definedExternally,
        after: String? = definedExternally
    ): SyntaxNode?

    fun getChild(type: Number, before: Number? = definedExternally): SyntaxNode?
    fun getChild(
        type: Number,
        before: Number? = definedExternally,
        after: Number? = definedExternally
    ): SyntaxNode?

    fun getChildren(
        type: String,
        before: String? = definedExternally,
        after: String? = definedExternally
    ): Array<SyntaxNode>

    fun getChildren(type: String): Array<SyntaxNode>
    fun getChildren(type: String, before: String? = definedExternally): Array<SyntaxNode>
    fun getChildren(
        type: String,
        before: String? = definedExternally,
        after: Number? = definedExternally
    ): Array<SyntaxNode>

    fun getChildren(
        type: String,
        before: Number? = definedExternally,
        after: String? = definedExternally
    ): Array<SyntaxNode>

    fun getChildren(type: String, before: Number? = definedExternally): Array<SyntaxNode>
    fun getChildren(
        type: String,
        before: Number? = definedExternally,
        after: Number? = definedExternally
    ): Array<SyntaxNode>

    fun getChildren(
        type: Number,
        before: String? = definedExternally,
        after: String? = definedExternally
    ): Array<SyntaxNode>

    fun getChildren(type: Number): Array<SyntaxNode>
    fun getChildren(type: Number, before: String? = definedExternally): Array<SyntaxNode>
    fun getChildren(
        type: Number,
        before: String? = definedExternally,
        after: Number? = definedExternally
    ): Array<SyntaxNode>

    fun getChildren(
        type: Number,
        before: Number? = definedExternally,
        after: String? = definedExternally
    ): Array<SyntaxNode>

    fun getChildren(type: Number, before: Number? = definedExternally): Array<SyntaxNode>
    fun getChildren(
        type: Number,
        before: Number? = definedExternally,
        after: Number? = definedExternally
    ): Array<SyntaxNode>

    override fun matchContext(context: Array<String>): Boolean
}

open external class TreeCursor : SyntaxNodeRef {
    override var type: NodeType
    override val name: String
    override val tree: Tree?
    override val node: SyntaxNode
    override var from: Number
    override var to: Number
    open var stack: Any
    open var bufferNode: Any
    open var yieldNode: Any
    open var yieldBuf: Any
    open var yield: Any
    open fun firstChild(): Boolean
    open fun lastChild(): Boolean
    open fun childAfter(pos: Number): Boolean
    open fun childBefore(pos: Number): Boolean
    open fun enter(
        pos: Number,
        side: String /* "-1" */,
        mode: IterMode = definedExternally
    ): Boolean

    open fun enter(pos: Number, side: String /* "-1" */): Boolean
    open fun enter(
        pos: Number,
        side: Number /* 0 | 1 */,
        mode: IterMode = definedExternally
    ): Boolean

    open fun enter(pos: Number, side: Number /* 0 | 1 */): Boolean
    open fun parent(): Boolean
    open fun nextSibling(): Boolean
    open fun prevSibling(): Boolean
    open var atLastNode: Any
    open var move: Any
    open fun next(enter: Boolean = definedExternally): Boolean
    open fun prev(enter: Boolean = definedExternally): Boolean
    open fun moveTo(
        pos: Number,
        side: String /* "-1" */ = definedExternally
    ): TreeCursor /* this */
    open fun moveTo(pos: Number): TreeCursor /* this */
    open fun moveTo(
        pos: Number,
        side: Number /* 0 | 1 */ = definedExternally
    ): TreeCursor /* this */

    open fun iterate(
        enter: (node: SyntaxNodeRef) -> Any,
        leave: (node: SyntaxNodeRef) -> Unit = definedExternally
    )

    override fun matchContext(context: Array<String>): Boolean
}
