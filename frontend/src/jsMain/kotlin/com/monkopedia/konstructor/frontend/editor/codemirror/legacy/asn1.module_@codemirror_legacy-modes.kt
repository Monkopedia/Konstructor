@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@codemirror/legacy-modes/mode/asn1")
@file:JsNonModule

package dukat.codemirror.legacymodes

import dukat.codemirror.language.StreamParser
import kotlin.js.Json

external interface `T$29` {
    var keywords: Json?
        get() = definedExternally
        set(value) = definedExternally
    var cmipVerbs: Json?
        get() = definedExternally
        set(value) = definedExternally
    var compareTypes: Json?
        get() = definedExternally
        set(value) = definedExternally
    var status: Json?
        get() = definedExternally
        set(value) = definedExternally
    var tags: Json?
        get() = definedExternally
        set(value) = definedExternally
    var storage: Json?
        get() = definedExternally
        set(value) = definedExternally
    var modifier: Json?
        get() = definedExternally
        set(value) = definedExternally
    var accessTypes: Json?
        get() = definedExternally
        set(value) = definedExternally
    var multiLineStrings: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external fun asn1(conf: `T$29`): StreamParser<Any>
