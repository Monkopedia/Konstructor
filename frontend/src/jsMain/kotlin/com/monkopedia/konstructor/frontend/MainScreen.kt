package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.LinearDimension
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.width
import kotlinx.html.id
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.child
import react.dom.attrs
import react.setState
import styled.css
import styled.styledDiv

external interface MainScreenProps : Props {
    var service: Konstructor
    var workspaceList: List<Space>?
    var onWorkspaceListChanged: ((List<Space>?) -> Unit)?
}

external interface MainScreenState : State {
    var currentWorkspaceId: String?
    var currentWorkspace: Workspace?

    var konstructionList: List<Konstruction>?

    var currentId: String?
    var currentKonstructionName: String?
    var currentKonstruction: KonstructionService?
}

class MainScreen : RComponent<MainScreenProps, MainScreenState>() {
    override fun RBuilder.render() {
        styledDiv {
            css {
                this.display = Display.flex
                this.flexDirection = FlexDirection.row
            }
            styledDiv {
                css {
                    width = LinearDimension("50%")
                }
                attrs { this.id = "container" }
            }
            styledDiv {
                css {
                    width = LinearDimension("50%")
                }
                styledDiv {
                    css {
                        this.display = Display.flex
                        this.flexDirection = FlexDirection.column
                    }
                    styledDiv {
                        css {
                            this.display = Display.flex
                            this.flexDirection = FlexDirection.row
                        }
                        child(WorkspaceSelector::class) {
                            attrs {
                                currentWorkspace = state.currentId
                                workspaces = props.workspaceList
                                onWorkspaceSelected = this@MainScreen::setWorkspace
                            }
                        }
                    }
                    child(CodeMirrorScreen::class) {
                    }
                }
            }
        }
    }

    private fun setWorkspace(id: String) {
        GlobalScope.launch {
            val workspace = props.service.get(id)
            setState {
                currentId = id
                currentWorkspace = workspace
            }
        }
    }
}