package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import com.monkopedia.konstructor.frontend.StateContext
import com.monkopedia.konstructor.frontend.WorkManager
import csstype.Display
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.important
import csstype.pct
import csstype.px
import emotion.react.css
import mui.material.AppBar
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useState

external interface MenuComponentProps : Props {
    var service: Konstructor

    var currentWorkspace: String?
    var workspaces: List<Space>?
    var onWorkspaceSelected: ((String) -> Unit)?
    var onWorkspacesChanged: ((List<Space>?) -> Unit)?

    var currentKonstruction: String?
    var konstructions: List<Konstruction>?
    var onKonstructionSelected: ((String) -> Unit)?
    var onKonstructionsChanged: ((List<Konstruction>?) -> Unit)?

    var workManager: WorkManager
    var currentWorkspaceService: Workspace?
    var currentKonstructionService: KonstructionService?
}

data class MenuComponentState(
    var editWorkspaceDialog: Boolean? = null,
    var createWorkspaceDialog: Boolean? = null,
    var editKonstructionDialog: Boolean? = null,
    var createKonstructionDialog: Boolean? = null,
    var lastTextInput: String? = null
)

val MenuComponent = FC<MenuComponentProps> { props ->
    var state = useState(MenuComponentState())
    val context = StateContext(state)

    val currentSpace = props.workspaces?.find { it.id == props.currentWorkspace }
    val currentKonstruction = props.konstructions?.find { it.id == props.currentKonstruction }
    AppBar {
        css {
            width = important(50.pct)
        }
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                justifyContent = JustifyContent.spaceBetween
                marginTop = 8.px
                marginBottom = 8.px
                marginLeft = 16.px
                marginRight = 16.px
            }
            div {
                WorkspaceSelector {
                    currentWorkspace = props.currentWorkspace
                    workspaces = props.workspaces
                    onWorkspaceSelected = props.onWorkspaceSelected
                }
                if (currentSpace != null) {
                    editWorkspaceButton {
                        this.menuProps = props
                        this.editContext = context
                        this.currentSpace = currentSpace
                    }
                }
                createWorkspaceButton {
                    this.menuProps = props
                    this.editContext = context
                    this.currentSpace = currentSpace
                }
            }
            div {
                if (currentSpace != null) {
                    if (props.konstructions?.isNotEmpty() == true) {
                        KonstructionSelector {
                            this.currentKonstruction = props.currentKonstruction
                            this.konstructions = props.konstructions
                            this.onKonstructionSelected = props.onKonstructionSelected
                        }
                    }
                    if (currentKonstruction != null) {
                        editKonstructionButton {
                            this.menuProps = props
                            this.editContext = context
                            this.currentSpace = currentSpace
                        }
                    }
                    createKonstructionButton {
                        this.menuProps = props
                        this.editContext = context
                        this.currentSpace = currentSpace
                    }
                }
            }
        }
    }
}

fun StateContext<MenuComponentState>.closeAllDialogs() {
    state = state.copy(
        editWorkspaceDialog = false,
        createWorkspaceDialog = false,
        editKonstructionDialog = false,
        createKonstructionDialog = false,
        lastTextInput = null
    )
}

external interface SubComponentProps : Props {
    var editContext: StateContext<MenuComponentState>
    var menuProps: MenuComponentProps
    var currentSpace: Space?
}

