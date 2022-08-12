@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@codemirror/legacy-modes/mode/properties")
@file:JsNonModule

package dukat.codemirror.legacymodes

import dukat.codemirror.language.StreamParser

external var properties: StreamParser<Any>
