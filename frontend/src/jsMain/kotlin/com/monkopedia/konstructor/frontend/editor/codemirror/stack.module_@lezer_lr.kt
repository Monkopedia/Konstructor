@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)
@file:JsModule("@lezer/lr")
@file:JsNonModule

package dukat.lezer.lr

open external class Stack {
    open var pos: Number
    open fun canShift(term: Number): Boolean
    open fun dialectEnabled(dialectID: Number): Boolean
    open var shiftContext: Any
    open var reduceContext: Any
    open var updateContext: Any
}
