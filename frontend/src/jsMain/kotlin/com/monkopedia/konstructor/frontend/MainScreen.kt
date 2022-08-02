package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import com.monkopedia.konstructor.frontend.editor.KonstructionEditor
import com.monkopedia.konstructor.frontend.menu.MenuComponent
import csstype.Display
import csstype.FlexDirection
import csstype.pct
import csstype.px
import emotion.react.css
import react.FC
import react.Props
import react.State
import react.dom.html.ReactHTML.div

external interface MainScreenProps : Props {
    var workManager: WorkManager
    var service: Konstructor
    var workspaceList: List<Space>?
    var onWorkspaceListChanged: ((List<Space>?) -> Unit)?
    var mainScreenState: StateContext<MainScreenState>
}

data class MainScreenState(
    val currentWorkspaceId: String? = null,
    val currentWorkspace: Workspace? = null,

    val konstructionList: List<Konstruction>? = null,

    val currentId: String? = null,
    val currentKonstruction: KonstructionService? = null
): State

val MainScreen = FC<MainScreenProps> { props ->
    var state by props.mainScreenState::state

    fun setKonstructions(konstructions: List<Konstruction>?) {
        state = state.copy(
            konstructionList = konstructions
        )
    }

    fun setKonstruction(id: String) {
        props.workManager.doWork {
            val konstruction =
                props.service.konstruction(
                    state.konstructionList?.find { it.id == id }
                        ?: error("Can't find $id")
                )
//            state.currentKonstruction?.close()
            state = state.copy(
                currentId = id,
                currentKonstruction = konstruction
            )
        }
    }

    fun setWorkspace(id: String) {
        props.workManager.doWork {
            val id = id
            val workspace = props.service.get(id)
//            state.currentWorkspace?.close()
            state = state.copy(
                currentWorkspaceId = id,
                currentWorkspace = workspace,
                currentId = null,
                konstructionList = null
            )
            val konstructionList = workspace.list(Unit)
            state = state.copy(
                currentWorkspaceId = id,
                currentWorkspace = workspace,
                currentId = null,
                konstructionList = konstructionList
            )
        }
    }
    div {
        css {
            this.display = Display.flex
            this.flexDirection = FlexDirection.row
        }
        div {
            css {
                width = 50.pct
            }
            GLComponent {
                this.konstruction = state.konstructionList?.find { it.id == state.currentId }
                this.konstructionService = state.currentKonstruction
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
                    service = props.service

                    currentWorkspace = state.currentWorkspaceId
                    workspaces = props.workspaceList
                    onWorkspaceSelected = ::setWorkspace
                    onWorkspacesChanged = props.onWorkspaceListChanged

                    currentKonstruction = state.currentId
                    konstructions = state.konstructionList
                    onKonstructionSelected = ::setKonstruction
                    onKonstructionsChanged = ::setKonstructions

                    currentWorkspaceService = state.currentWorkspace
                    currentKonstructionService = state.currentKonstruction
                    workManager = props.workManager
                }
                div {
                    css {
                        height = 64.px
                    }
                }
                KonstructionEditor {
                    konstruction = state.konstructionList?.find { it.id == state.currentId }
                    konstructionService = state.currentKonstruction
                }
            }
        }
    }
}
