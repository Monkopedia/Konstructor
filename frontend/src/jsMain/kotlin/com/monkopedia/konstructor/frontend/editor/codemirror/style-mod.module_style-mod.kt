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
