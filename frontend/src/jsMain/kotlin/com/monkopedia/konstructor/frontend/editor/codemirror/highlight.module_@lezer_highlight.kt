@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@lezer/highlight")
@file:JsNonModule

package dukat.lezer.highlight

import dukat.lezer.common.NodeType

open external class Tag {
    open val set: Array<Tag>

    companion object {
        fun define(parent: Tag = definedExternally): Tag
        fun defineModifier(): (tag: Tag) -> Tag
    }
}

external interface Highlighter {
    fun style(tags: Array<Tag>): String?
    val scope: ((node: NodeType) -> Boolean)?
}
