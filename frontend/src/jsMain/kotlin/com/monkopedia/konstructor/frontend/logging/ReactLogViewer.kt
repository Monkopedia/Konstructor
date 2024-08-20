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
package com.monkopedia.konstructor.frontend.logging

import com.monkopedia.konstructor.frontend.editor.MirrorStyles
import com.monkopedia.konstructor.frontend.utils.buildExt
import com.monkopedia.konstructor.frontend.utils.cleanup
import dukat.codemirror.state.EditorState
import dukat.codemirror.view.EditorView
import dukat.codemirror.vim.CodeMirror
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.memo
import react.useEffect
import react.useRef
import web.html.HTMLDivElement

external interface LogViewerProps : Props {
    var editorState: EditorState
    var setView: (EditorView?) -> Unit
    var target: String
}

val LogViewer = memo(
    FC<LogViewerProps> { props ->
        MirrorStyles.errorLineBackground
        MirrorStyles.inject()
        val textAreaRef = useRef<HTMLDivElement>()

        ReactHTML.div {
            this.ref = textAreaRef
        }

        useEffect(textAreaRef, props.editorState) {
            val textArea = textAreaRef.current!!
            val cm = EditorView(
                buildExt {
                    this.state = props.editorState
                    this.parent = textArea
                }
            )

            CodeMirror(cm)
            props.setView(cm)
            cleanup {
                props.setView(null)
                cm.destroy()
            }
        }
    }
) { oldProps, newProps ->
    oldProps.editorState === newProps.editorState &&
        oldProps.target == newProps.target
}
