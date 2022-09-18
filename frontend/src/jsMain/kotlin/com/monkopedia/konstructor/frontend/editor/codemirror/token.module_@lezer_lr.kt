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
@file:JsModule("@lezer/lr")
@file:JsNonModule
package dukat.lezer.lr

open external class CachedToken {
    open var start: Number
    open var value: Number
    open var end: Number
    open var extended: Number
    open var lookAhead: Number
    open var mask: Number
    open var context: Number
}

open external class InputStream {
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

open external class ExternalTokenizer(
    token: (input: InputStream, stack: Stack) -> Unit,
    options: ExternalOptions = definedExternally
)
