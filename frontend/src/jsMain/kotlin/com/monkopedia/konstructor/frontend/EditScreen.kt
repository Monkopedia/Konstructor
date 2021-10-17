package com.monkopedia.konstructor.frontend

import kotlinext.js.js
import kotlinx.html.TEXTAREA
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.attrs
import react.dom.defaultValue
import styled.css
import styled.styledTextarea

external interface CodeMirrorProps : RProps {
    var content: String?
}

class CodeMirrorScreen(props: CodeMirrorProps) :
    RComponent<CodeMirrorProps, RState>(props) {
    val textAreaRef = createRef<TEXTAREA>()

    override fun RBuilder.render() {
        styledTextarea {
            attrs {
                this.defaultValue = props.content.toString()
            }
            css {
            }
            this.ref = textAreaRef
        }
    }

    override fun componentDidMount() {
        val onSave: () -> Unit = this::onSave
        commands.save = onSave
        fromTextArea(
            textAreaRef.current!!,
            js {
                mode = js {
                    name = "gfm"
                }
                keyMap = "vim"
                lineNumbers = true
                this.theme = "default"
                this.showCorsorWhenSelecting = true
            }
        )
    }

    private fun onSave() {
        println("onSave")
    }
}
