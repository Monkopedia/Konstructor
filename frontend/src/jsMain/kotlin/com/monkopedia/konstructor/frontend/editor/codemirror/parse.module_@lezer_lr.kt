@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@lezer/lr")
@file:JsNonModule

package dukat.lezer.lr

import dukat.lezer.common.Input
import dukat.lezer.common.NodePropSource
import dukat.lezer.common.NodeSet
import dukat.lezer.common.ParseWrapper
import dukat.lezer.common.Parser
import dukat.lezer.common.PartialParse
import dukat.lezer.common.Tree
import dukat.lezer.common.TreeFragment
import dukat.lezer.common.`T$32`

external interface `T$36`<T> {
    var start: T
    val shift: ((context: T, term: Number, stack: Stack, input: InputStream) -> T)?
    val reduce: ((context: T, term: Number, stack: Stack, input: InputStream) -> T)?
    val reuse: ((context: T, node: Tree, stack: Stack, input: InputStream) -> T)?
    val hash: ((context: T) -> Number)?
    var strict: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

open external class ContextTracker<T>(spec: `T$36`<T>)

external interface `T$37` {
    var from: ExternalTokenizer
    var to: ExternalTokenizer
}

external interface `T$38` {
    var from: (value: String, stack: Stack) -> Number
    var to: (value: String, stack: Stack) -> Number
}

external interface ParserConfig {
    var props: Array<NodePropSource>?
        get() = definedExternally
        set(value) = definedExternally
    var top: String?
        get() = definedExternally
        set(value) = definedExternally
    var dialect: String?
        get() = definedExternally
        set(value) = definedExternally
    var tokenizers: Array<`T$37`>?
        get() = definedExternally
        set(value) = definedExternally
    var specializers: Array<`T$38`>?
        get() = definedExternally
        set(value) = definedExternally
    var contextTracker: ContextTracker<Any>?
        get() = definedExternally
        set(value) = definedExternally
    var strict: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var wrap: ParseWrapper?
        get() = definedExternally
        set(value) = definedExternally
    var bufferLength: Number?
        get() = definedExternally
        set(value) = definedExternally
}

open external class LRParser : Parser {
    open val nodeSet: NodeSet
    override fun createParse(
        input: Input,
        fragments: Array<TreeFragment>,
        ranges: Array<`T$32`>
    ): PartialParse

    open fun configure(config: ParserConfig): LRParser
    open fun hasWrappers(): Boolean
    open fun getName(term: Number): String

    companion object {
        fun deserialize(spec: Any): LRParser
    }
}
