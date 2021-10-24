package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.LinearDimension
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.id
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.dom.attrs
import react.setState
import styled.css
import styled.styledDiv

external interface MainScreenProps : Props {
    var workManager: WorkManager
    var service: Konstructor
    var workspaceList: List<Space>?
    var onWorkspaceListChanged: ((List<Space>?) -> Unit)?
}

external interface MainScreenState : State {
    var currentWorkspaceId: String?
    var currentWorkspace: Workspace?

    var konstructionList: List<Konstruction>?

    var currentId: String?
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
                child(GLComponent::class) {
                    attrs {
                        this.konstruction = state.konstructionList?.find { it.id == state.currentId}
                        this.konstructionService = state.currentKonstruction
                    }
                }
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
                    child(MenuComponent::class) {
                        attrs {
                            service = props.service

                            currentWorkspace = state.currentWorkspaceId
                            workspaces = props.workspaceList
                            onWorkspaceSelected = this@MainScreen::setWorkspace
                            onWorkspacesChanged = props.onWorkspaceListChanged

                            currentKonstruction = state.currentId
                            konstructions = state.konstructionList
                            onKonstructionSelected = this@MainScreen::setKonstruction
                            onKonstructionsChanged = this@MainScreen::setKonstructions

                            currentWorkspaceService = state.currentWorkspace
                            currentKonstructionService = state.currentKonstruction
                            workManager = props.workManager
                        }
                    }
                    styledDiv {
                        css {
                            height = 64.px
                        }
                    }
                    child(KonstructionEditor::class) {
                        attrs {
                            konstruction = state.konstructionList?.find { it.id == state.currentId }
                            konstructionService = state.currentKonstruction
                        }
                    }
                }
            }
        }
    }

    private fun setKonstructions(konstructions: List<Konstruction>?) {
        setState {
            konstructionList = konstructions
        }
    }

    private fun setKonstruction(id: String) {
        props.workManager.doWork {
            val konstruction = props.service.konstruction(state.konstructionList?.find { it.id == id } ?: error("Can't find $id"))
//            state.currentKonstruction?.close()
            setState {
                currentId = id
                currentKonstruction = konstruction
            }
        }
    }

    private fun setWorkspace(id: String) {
        props.workManager.doWork {
            val workspace = props.service.get(id)
//            state.currentWorkspace?.close()
            setState {
                currentWorkspaceId = id
                currentWorkspace = workspace
                currentId = null
                konstructionList = null
            }
            val konstructionList = workspace.list(Unit)
            setState {
                this.konstructionList = konstructionList
            }
        }
    }
}
