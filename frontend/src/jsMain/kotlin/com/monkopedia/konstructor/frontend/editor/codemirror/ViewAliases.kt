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
package dukat.codemirror.view

import dukat.codemirror.commands.`T$0`
import dukat.codemirror.language.IndentContext
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.Facet
import dukat.codemirror.state.RangeSet
import dukat.codemirror.state.RangeValue
import dukat.codemirror.state.StateEffect
import dukat.codemirror.state.Transaction
import dukat.lezer.common.NodeType
import dukat.lezer.common.Tree
import org.w3c.dom.events.MouseEvent
import kotlin.js.Json

typealias DecorationSet = RangeSet<Decoration>

typealias MakeSelectionStyle = (view: EditorView, event: MouseEvent) -> MouseSelectionStyle?

typealias DOMEventHandlers<This> = Any

typealias Command = (target: EditorView) -> Boolean

typealias MakeTree = (
    children: Array<dynamic /* Tree | TreeBuffer */>,
    positions: Array<Number>,
    length: Number
) -> Tree
typealias LanguageDataFacet = (
    state: EditorState,
    pos: Number,
    side: dynamic /* 0 | 1 | "-1" */
) -> Array<Json>

typealias NodePropSource = (type: NodeType) -> dynamic

typealias TextIterator = Iterable<String>
typealias StateCommand = (target: `T$7`) -> Boolean
typealias RangeFilter<T> = (from: Number, to: Number, value: T) -> Boolean

typealias InputHandlerFacet = (view: EditorView, from: Number, to: Number, text: String) -> Boolean
typealias DragMovesSelectionFacet = (event: MouseEvent) -> Boolean
typealias ClickAddsSelectionRangeFacet = (event: MouseEvent) -> Boolean
typealias CASRFA = Array<ClickAddsSelectionRangeFacet>
typealias ClickAddsSelection = Facet<ClickAddsSelectionRangeFacet, CASRFA>
typealias DecorationsFacet = Any? /* DecorationSet | (view: EditorView) -> DecorationSet */
typealias AtomicRangesFacet = (view: EditorView) -> RangeSet<out RangeValue<*>>
typealias ScrollMarginsFacet = (view: EditorView) -> RectPartial?
typealias ContentAttributesFacet = Any? /* Attrs | (view: EditorView) -> Attrs? */
typealias EditorAttributesFacet = Any? /* Attrs | (view: EditorView) -> Attrs? */

typealias TransactionFilterFacet = (tr: Transaction) -> dynamic
typealias TransactionExtenderFacet = (tr: Transaction) -> dynamic

typealias IndentServiceFacet = (
    context: IndentContext,
    pos: Number
) -> Number?
typealias FoldServiceFacet = (state: EditorState, lineStart: Number, lineEnd: Number) -> `T$0`?

typealias FacetMethod = (tr: Transaction) -> Array<StateEffect<Any>>

