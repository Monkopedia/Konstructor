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
@file:JsModule("@codemirror/legacy-modes/mode/sql")
@file:JsNonModule

package dukat.codemirror.legacymodes

import dukat.codemirror.language.StreamParser
import kotlin.js.Json
import kotlin.js.RegExp

external interface `T$31` {
    var client: Json?
        get() = definedExternally
        set(value) = definedExternally
    var atoms: Json?
        get() = definedExternally
        set(value) = definedExternally
    var builtin: Json?
        get() = definedExternally
        set(value) = definedExternally
    var keywords: Json?
        get() = definedExternally
        set(value) = definedExternally
    var operatorChars: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var support: Json?
        get() = definedExternally
        set(value) = definedExternally
    var hooks: Json?
        get() = definedExternally
        set(value) = definedExternally
    var dateSQL: Json?
        get() = definedExternally
        set(value) = definedExternally
    var backslashStringEscapes: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var brackets: RegExp?
        get() = definedExternally
        set(value) = definedExternally
    var punctuation: RegExp?
        get() = definedExternally
        set(value) = definedExternally
}

external fun sql(conf: `T$31`): StreamParser<Any>

external var standardSQL: StreamParser<Any>

external var msSQL: StreamParser<Any>

external var mySQL: StreamParser<Any>

external var mariaDB: StreamParser<Any>

external var sqlite: StreamParser<Any>

external var cassandra: StreamParser<Any>

external var plSQL: StreamParser<Any>

external var hive: StreamParser<Any>

external var pgSQL: StreamParser<Any>

external var gql: StreamParser<Any>

external var gpSQL: StreamParser<Any>

external var sparkSQL: StreamParser<Any>

external var esper: StreamParser<Any>
