package dukat.codemirror.state

typealias TextIterator = Iterable<String>

typealias StateCommand = (target: `T$7`) -> Boolean

typealias RangeFilter<T> = (from: Number, to: Number, value: T) -> Boolean
