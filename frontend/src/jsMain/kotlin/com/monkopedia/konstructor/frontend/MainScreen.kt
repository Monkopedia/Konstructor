package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.frontend.editor.KonstructionEditor
import com.monkopedia.konstructor.frontend.gl.GLScreen
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.menu.MenuComponent
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Display
import csstype.FlexDirection
import csstype.pct
import csstype.px
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.memo

external interface MainScreenProps : Props {
    var workManager: WorkManager
}

val MainScreen = memo(
    FC<MainScreenProps> { props ->
        val konstructionScope = RootScope.scopeTracker.konstruction.useCollected()

        div {
            css {
                this.display = Display.flex
                this.flexDirection = FlexDirection.row
            }
            div {
                css {
                    width = 50.pct
                }
                konstructionScope?.let { scope ->
                    GLScreen {
                        this.konstructionScope = scope
                    }
                }
            }
            div {
                css {
                    width = 50.pct
                }
                div {
                    css {
                        this.display = Display.flex
                        this.flexDirection = FlexDirection.column
                    }
                    MenuComponent {
                        workManager = props.workManager
                    }
                    div {
                        css {
                            height = 64.px
                        }
                    }
                    konstructionScope?.let { konstructionScope ->
                        KonstructionEditor {
                            konstructionModel = konstructionScope.scope.get()
                            workspaceId = konstructionModel.workspaceId
                            konstructionId = konstructionModel.konstructionId
                        }
                    }
                }
            }
        }
    }
) { oldProps, newProps ->
    oldProps.workManager == newProps.workManager
}
