@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@codemirror/legacy-modes/mode/xml")
@file:JsNonModule

package dukat.codemirror.legacymodes

import dukat.codemirror.language.StreamParser

external var xml: StreamParser<Any>

external var html: StreamParser<Any>
