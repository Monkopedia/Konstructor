@file:Suppress("INTERFACE_WITH_SUPERCLASS", "OVERRIDING_FINAL_MEMBER", "RETURN_TYPE_MISMATCH_ON_OVERRIDE", "CONFLICTING_OVERLOADS")
@file:JsModule("@lezer/lr")
@file:JsNonModule
package dukat.lezer.lr

import kotlin.js.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import org.w3c.dom.parsing.*
import org.w3c.dom.svg.*
import org.w3c.dom.url.*
import org.w3c.fetch.*
import org.w3c.files.*
import org.w3c.notifications.*
import org.w3c.performance.*
import org.w3c.workers.*
import org.w3c.xhr.*

external open class CachedToken {
    open var start: Number
    open var value: Number
    open var end: Number
    open var extended: Number
    open var lookAhead: Number
    open var mask: Number
    open var context: Number
}

external open class InputStream {
    open var chunk2: Any
    open var chunk2Pos: Any
    open var next: Number
    open var pos: Number
    open var rangeIndex: Any
    open var range: Any
    open fun peek(offset: Number): Any
    open fun acceptToken(token: Number, endOffset: Number = definedExternally)
    open var getChunk: Any
    open var readNext: Any
    open fun advance(n: Number = definedExternally): Number
    open var setDone: Any
}

external interface Tokenizer

external interface ExternalOptions {
    var contextual: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var fallback: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var extend: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external open class ExternalTokenizer(token: (input: InputStream, stack: Stack) -> Unit, options: ExternalOptions = definedExternally)