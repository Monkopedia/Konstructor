package com.monkopedia.konstructor.frontend.editor

import dukat.codemirror.basicSetup
import dukat.codemirror.state.EditorState
import dukat.codemirror.state.EditorStateConfig
import dukat.codemirror.state.StateEffect
import dukat.codemirror.state.`T$5`
import dukat.codemirror.state.Transaction
import dukat.codemirror.state.TransactionSpec
import dukat.codemirror.view.EditorView
import dukat.codemirror.view.EditorViewConfig
import dukat.codemirror.view.`T$10`
import dukat.codemirror.view.`T$11`
import dukat.codemirror.view.ViewUpdate
import kotlinext.js.js
import org.w3c.dom.HTMLDivElement
import react.PropsWithRef
import react.StateInstance
import react.dom.div
import react.forwardRef
import react.useEffect
import react.useImperativeHandle
import react.useRef
import react.useState

/**
 * Migrated to kotlin from react-codemirror component.
 *
 * MIT License
 *
 * Copyright (c) 2021 uiw
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

external interface ReactCodeMirrorProps : PropsWithRef<ReactCodeMirrorRef> {
    var className: String?
    var selection: dynamic /* EditorSelection? | `T$0`? */

    /** value of the auto created model in the editor. */
    var value: String?
    var height: String?
    var minHeight: String?
    var maxHeight: String?
    var width: String?
    var minWidth: String?
    var maxWidth: String?

    /** focus on the editor. */
    var autoFocus: Boolean?

    /** Enables a placeholder—a piece of example content to show when the editor is empty. */
    var placeholder: dynamic? /*String? | HTMLElement*/

    /**
     * `light` / `dark` / `Extension` Defaults to `light`.
     * @default light
     */
    var theme: dynamic? /*?: 'light' | 'dark' | Extension*/

    /**
     * Whether to optional basicSetup by default
     * @default true
     */
    var basicSetup: dynamic/*?: boolean | BasicSetupOptions*/

    /**
     * This disables editing of the editor content by the user.
     * @default true
     */
    var editable: Boolean?

    /**
     * This disables editing of the editor content by the user.
     * @default false
     */
    var readOnly: Boolean?

    /**
     * Whether to optional basicSetup by default
     * @default true
     */
    var indentWithTab: Boolean?

    /** Fired whenever a change occurs to the document. */
    var onChange: ((value: String, viewUpdate: ViewUpdate) -> Unit)?

    /** Fired whenever any state change occurs within the editor, including non-document changes like lint results. */
    var onUpdate: ((viewUpdate: ViewUpdate) -> Unit)?

    /** The first time the editor executes the event. */
    var onCreateEditor: ((view: EditorView, state: EditorState) -> Unit)?

    /**
     * Extension values can be [provided](https://codemirror.net/6/docs/ref/#state.EditorStateConfig.extensions) when creating a state to attach various kinds of configuration and behavior information.
     * They can either be built-in extension-providing objects,
     * such as [state fields](https://codemirror.net/6/docs/ref/#state.StateField) or [facet providers](https://codemirror.net/6/docs/ref/#state.Facet.of),
     * or objects with an extension in its `extension` property. Extensions can be nested in arrays arbitrarily deep—they will be flattened when processed.
     */
    var extensions: Array<dynamic>?

    /**
     * If the view is going to be mounted in a shadow root or document other than the one held by the global variable document (the default), you should pass it here.
     * Originally from the [config of EditorView](https://codemirror.net/6/docs/ref/#view.EditorView.constructor%5Econfig.root)
     */
    var root: dynamic? /*ShadowRoot | Document*/
}

external interface ReactCodeMirrorRef {
    var editor: HTMLDivElement?
    var state: EditorState?
    var view: EditorView?
}

val ReactCodeMirror = forwardRef<ReactCodeMirrorRef, ReactCodeMirrorProps> { props, ref ->
    val editor = useRef<HTMLDivElement>(null)
    val states = useCodeMirror(props, editor.current)

    useImperativeHandle(
        ref,
        editor,
        states.container,
        states.state,
        states.view
    ) {
        (js { } as ReactCodeMirrorRef).apply {
            this.editor = editor.current
            this.state = state
            this.view = view
        }
    }

    // check type of value
    require(props.value is String) {
        "value must be typeof string but got ${props.value?.let { it::class }}"
    }

    val defaultClassNames = (props.theme as? String)?.let { "cm-theme-$it" } ?: "cm-theme"
    return@forwardRef div(
        classes = defaultClassNames + (props.className?.let { " $it " } ?: "")
    ) {
        this.ref = editor
    }
}

class CodeMirrorStates(
    containerInstance: StateInstance<HTMLDivElement?>,
    viewInstance: StateInstance<EditorView?>,
    stateInstance: StateInstance<EditorState?>
) {
    val containerGet = containerInstance.component1()
    val containerSet = containerInstance.component2()
    var container: HTMLDivElement?
        inline get() = containerGet
        inline set(value) {
            containerSet(value)
        }
    val viewGet = viewInstance.component1()
    val viewSet = viewInstance.component2()
    var view: EditorView?
        inline get() = viewGet
        inline set(value) {
            viewSet(value)
        }
    val stateGet = stateInstance.component1()
    val stateSet = stateInstance.component2()
    var state: EditorState?
        inline get() = stateGet
        inline set(value) {
            stateSet(value)
        }
}

fun useCodeMirror(props: ReactCodeMirrorProps, container: HTMLDivElement?): CodeMirrorStates {
    val states = CodeMirrorStates(useState(), useState(), useState())
    val defaultLightThemeOption = EditorView.theme(
        js {
            this["&"] = js {
                this.backgroundColor = "#fff"
            }
        } as `T$10`,
        js {
            this.dark = folse
        } as `T$11`
    ).maybeArray()
    val defaultThemeOption = EditorView.theme(
        js {
            this["&"] = js {
                this.height = props.height
                this.minHeight = props.minHeight
                this.maxHeight = props.maxHeight
                this.width = props.width
                this.minWidth = props.minWidth
                this.maxWidth = props.maxWidth
            }
        } as `T$10`
    )
    val updateListener = EditorView.updateListener.of { vu: ViewUpdate ->
        if (vu.asDynamic().docChanged as Boolean) {
            val doc = vu.state.doc
            val value = doc.toString()
            props.onChange?.invoke(value, vu)
        }
    }

    val getExtensions = mutableListOf(updateListener, defaultThemeOption)
//    if (props.indentWithTab) {
//        getExtensions.add(0, keymap.of(arrayOf(props.indentWithTab)));
//    }
    if (props.basicSetup != false) {
        val basics = (
            if (props.basicSetup is Boolean || props.basicSetup == null) {
                basicSetup
            } else {
                props.basicSetup
            } as Any
            )
        println("basics $basics")
        getExtensions.add(0, basics)
    }

    when (props.theme ?: "light") {
        "light" -> getExtensions.addAll(defaultLightThemeOption.toList())
        "dark" -> getExtensions.add("darkula")
        else -> getExtensions.add(props.theme)
    }

    if (props.editable == false) {
        getExtensions.add(EditorView.editable.of(false))
    }
    if (props.readOnly == true) {
        getExtensions.add(EditorState.readOnly.of(true))
    }

    props.onUpdate?.let { onUpdate ->
        getExtensions.add(EditorView.updateListener.of(onUpdate))
    }
    props.extensions?.let { getExtensions.addAll(it) }

    useEffect(states.container, states.state) {
        if (states.container != null && states.state == null) {
            val stateCurrent = EditorState.create(
                (js { } as EditorStateConfig).apply {
                    this.doc = props.value
                    this.selection = props.selection
                    this.extensions = getExtensions.toTypedArray()
                }
            )
            states.state = stateCurrent
            if (states.view == null) {
                val viewCurrent = EditorView(
                    (js { } as EditorViewConfig).apply {
                        this.state = stateCurrent
                        this.parent = container
                        this.root = props.root
                    }
                )
                states.view = (viewCurrent)
                props.onCreateEditor?.invoke(viewCurrent, stateCurrent)
            }
        }
        cleanup {
            if (states.view != null) {
                states.state = undefined
                states.view = undefined
            }
        }
    }

    useEffect(container) {
        states.container = container
    }

    useEffect(states.view) {
        cleanup {
            states.view?.destroy()
            states.view = undefined
        }
    }

    useEffect(props.autoFocus, states.view) {
        if (props.autoFocus == true) {
            states.view?.focus()
        }
    }

    useEffect(
        props.theme,
        props.extensions,
        props.height,
        props.minHeight,
        props.maxHeight,
        props.width,
        props.minWidth,
        props.maxWidth,
        props.placeholder,
        props.editable,
        props.readOnly,
        props.indentWithTab,
        props.basicSetup,
        props.onChange,
        props.onUpdate
    ) {
        states.view?.dispatch(
            js {
                effects = arrayOf(StateEffect.reconfigure.of(getExtensions.toTypedArray()))
            } as Transaction
        )
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }

    useEffect(props.value, states.view) {
        val currentValue = states.state?.doc?.toString() ?: ""
        if (props.value !== currentValue) {
            states.view?.dispatch(
                (js { } as TransactionSpec).apply {
                    changes = (js { } as `T$5`).apply {
                        from = 0
                        to = currentValue.length
                        insert = props.value ?: ""
                    }
                }
            )
        }
    }

    return states
}

fun Any.maybeArray(): Array<dynamic> {
    return (this as? Array<dynamic>) ?: arrayOf(this)
}
