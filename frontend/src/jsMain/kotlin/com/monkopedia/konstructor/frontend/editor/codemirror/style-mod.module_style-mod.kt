@file:Suppress(
    "INTERFACE_WITH_SUPERCLASS",
    "OVERRIDING_FINAL_MEMBER",
    "RETURN_TYPE_MISMATCH_ON_OVERRIDE",
    "CONFLICTING_OVERLOADS"
)

@file:JsModule("style-mod")
@file:JsNonModule

package dukat.stylemod

import org.w3c.dom.Document
import org.w3c.dom.DocumentOrShadowRoot
import org.w3c.dom.ShadowRoot

external interface `T$44` {
    @nativeGetter
    operator fun get(selector: String): StyleSpec?

    @nativeSetter
    operator fun set(selector: String, value: StyleSpec)
}

external interface `T$45` {
    val finish: ((sel: String) -> String)?
}

open external class StyleModule(spec: `T$44`, options: `T$45` = definedExternally) {
    open fun getRules(): String

    companion object {
        fun mount(root: Document, module: StyleModule)
        fun mount(root: Document, module: Array<StyleModule>)
        fun mount(root: ShadowRoot, module: StyleModule)
        fun mount(root: ShadowRoot, module: Array<StyleModule>)
        fun mount(root: DocumentOrShadowRoot, module: StyleModule)
        fun mount(root: DocumentOrShadowRoot, module: Array<StyleModule>)
        fun newName(): String
    }
}

external interface StyleSpec {
    @nativeGetter
    operator fun get(propOrSelector: String): dynamic /* String? | Number? | StyleSpec? */

    @nativeSetter
    operator fun set(propOrSelector: String, value: String?)

    @nativeSetter
    operator fun set(propOrSelector: String, value: Number?)

    @nativeSetter
    operator fun set(propOrSelector: String, value: StyleSpec?)
}
