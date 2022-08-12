package dukat.codemirror.view

import dukat.codemirror.state.RangeSet
import org.w3c.dom.events.MouseEvent

typealias DecorationSet = RangeSet<Decoration>

typealias MakeSelectionStyle = (view: EditorView, event: MouseEvent) -> MouseSelectionStyle?

typealias DOMEventHandlers<This> = Any

typealias Command = (target: EditorView) -> Boolean