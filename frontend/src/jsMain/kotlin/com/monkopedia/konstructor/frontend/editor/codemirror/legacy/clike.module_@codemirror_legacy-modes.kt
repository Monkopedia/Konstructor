@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@codemirror/legacy-modes/mode/clike")
@file:JsNonModule

package dukat.codemirror.legacymodes

import dukat.codemirror.language.StreamParser
import kotlin.js.Json
import kotlin.js.RegExp

external interface `T$30` {
    var statementIndentUnit: Number?
        get() = definedExternally
        set(value) = definedExternally
    var dontAlignCalls: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var keywords: Json?
        get() = definedExternally
        set(value) = definedExternally
    var types: Json?
        get() = definedExternally
        set(value) = definedExternally
    var builtin: Json?
        get() = definedExternally
        set(value) = definedExternally
    var blockKeywords: Json?
        get() = definedExternally
        set(value) = definedExternally
    var atoms: Json?
        get() = definedExternally
        set(value) = definedExternally
    var hooks: Json?
        get() = definedExternally
        set(value) = definedExternally
    var multiLineStrings: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var indentStatements: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var indentSwitch: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var namespaceSeparator: String?
        get() = definedExternally
        set(value) = definedExternally
    var isPunctuationChar: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var numberStart: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var number: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var isOperatorChar: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var isIdentifierChar: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var isReservedIdentifier: ((id: String) -> Boolean)?
        get() = definedExternally
        set(value) = definedExternally
}

external fun clike(conf: `T$30`): StreamParser<Any>

external var c: StreamParser<Any>

external var cpp: StreamParser<Any>

external var java: StreamParser<Any>

external var csharp: StreamParser<Any>

external var scala: StreamParser<Any>

external var kotlin: StreamParser<Any>

external var shader: StreamParser<Any>

external var nesC: StreamParser<Any>

external var objectiveC: StreamParser<Any>

external var objectiveCpp: StreamParser<Any>

external var squirrel: StreamParser<Any>

external var ceylon: StreamParser<Any>

external var dart: StreamParser<Any>
