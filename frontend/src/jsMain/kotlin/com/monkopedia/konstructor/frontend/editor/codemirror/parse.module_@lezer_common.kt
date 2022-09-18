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

external interface ChangedRange {
    var fromA: Number
    var toA: Number
    var fromB: Number
    var toB: Number
}

open external class TreeFragment(
    from: Number,
    to: Number,
    tree: Tree,
    offset: Number,
    openStart: Boolean = definedExternally,
    openEnd: Boolean = definedExternally
) {
    open val from: Number
    open val to: Number
    open val tree: Tree
    open val offset: Number

    companion object {
        fun addTree(
            tree: Tree,
            fragments: Array<TreeFragment> = definedExternally,
            partial: Boolean = definedExternally
        ): Array<TreeFragment>

        fun applyChanges(
            fragments: Array<TreeFragment>,
            changes: Array<ChangedRange>,
            minGap: Number = definedExternally
        ): Array<TreeFragment>
    }
}

external interface PartialParse {
    fun advance(): Tree?
    val parsedPos: Number
    fun stopAt(pos: Number)
    val stoppedAt: Number?
}

open external class Parser {
    open fun createParse(
        input: Input,
        fragments: Array<TreeFragment>,
        ranges: Array<`T$32`>
    ): PartialParse

    open fun startParse(
        input: Input,
        fragments: Array<TreeFragment> = definedExternally,
        ranges: Array<`T$32`> = definedExternally
    ): PartialParse

    open fun startParse(input: Input): PartialParse
    open fun startParse(
        input: Input,
        fragments: Array<TreeFragment> = definedExternally
    ): PartialParse

    open fun startParse(
        input: String,
        fragments: Array<TreeFragment> = definedExternally,
        ranges: Array<`T$32`> = definedExternally
    ): PartialParse

    open fun startParse(input: String): PartialParse
    open fun startParse(
        input: String,
        fragments: Array<TreeFragment> = definedExternally
    ): PartialParse

    open fun parse(
        input: Input,
        fragments: Array<TreeFragment> = definedExternally,
        ranges: Array<`T$32`> = definedExternally
    ): Tree

    open fun parse(input: Input): Tree
    open fun parse(input: Input, fragments: Array<TreeFragment> = definedExternally): Tree
    open fun parse(
        input: String,
        fragments: Array<TreeFragment> = definedExternally,
        ranges: Array<`T$32`> = definedExternally
    ): Tree

    open fun parse(input: String): Tree
    open fun parse(input: String, fragments: Array<TreeFragment> = definedExternally): Tree
}

external interface Input {
    val length: Number
    fun chunk(from: Number): String
    val lineChunks: Boolean
    fun read(from: Number, to: Number): String
}
