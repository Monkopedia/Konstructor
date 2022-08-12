@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@codemirror/search")
@file:JsNonModule

package dukat.codemirror.search

import dukat.codemirror.state.Range
import org.w3c.dom.Text

external interface `T$49` {
    var search: String
    var caseSensitive: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var literal: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var regexp: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var replace: String?
        get() = definedExternally
        set(value) = definedExternally
}

open external class SearchQuery(config: `T$49`) {
    open val search: String
    open val caseSensitive: Boolean
    open val regexp: Boolean
    open val replace: String
    open val valid: Boolean
    open fun eq(other: SearchQuery): Boolean
    open fun getCursor(
        doc: Text,
        from: Number = definedExternally,
        to: Number = definedExternally
    ): Iterator<Range<Nothing>>
}
