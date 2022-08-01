package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import csstype.Display
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.important
import csstype.pct
import csstype.px
import emotion.react.css
import mui.icons.material.Add
import mui.icons.material.Edit
import mui.material.AppBar
import mui.material.Button
import mui.material.ButtonColor
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogContent
import mui.material.DialogTitle
import mui.material.FormControlVariant
import mui.material.IconButton
import mui.material.TextField
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.onChange
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

private fun StateContext<MenuComponentState>.closeAllDialogs() {
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

val editWorkspaceButton = FC<SubComponentProps> { props ->
    IconButton {
        Edit()
        onClick = {
            props.editContext.state = props.editContext.state.copy(
                editWorkspaceDialog = true
            )
        }
    }
    Dialog {
        open = props.editContext.state.editWorkspaceDialog ?: false
        onClose = { _, _ ->
            props.editContext.closeAllDialogs()
        }
        DialogTitle {
            +"Change workspace name"
        }
        DialogContent {
            TextField {
                +"New workspace name"
                defaultValue = props.currentSpace?.name
                variant = FormControlVariant.outlined
                onChange = { e ->
                    props.editContext.state = props.editContext.state.copy(
                        lastTextInput = e.target.asDynamic().value.toString()
                    )
                }
            }
        }
        DialogActions {
            Button {
                +"Cancel"
                color = ButtonColor.secondary
                onClick = {
                    props.editContext.closeAllDialogs()
                }
            }
            Button {
                +"Create"
                color = ButtonColor.primary
                onClick = {
                    val lastTextInput = props.editContext.state.lastTextInput
                    props.menuProps.workManager.doWork {
                        if (lastTextInput == null || lastTextInput == props.currentSpace?.name) {
                            props.editContext.closeAllDialogs()
                            return@doWork
                        }
                        props.menuProps.currentWorkspaceService?.setName(lastTextInput)
                        val updatedWorkspaces = props.menuProps.workspaces?.map {
                            if (it.id == props.menuProps.currentWorkspace) {
                                it.copy(name = lastTextInput)
                            } else it
                        }
                        props.menuProps.onWorkspacesChanged?.invoke(updatedWorkspaces)
                        props.editContext.closeAllDialogs()
                    }
                }
            }
        }
    }
}

val createWorkspaceButton = FC<SubComponentProps> { props ->
    IconButton {
        Add()
        onClick = {
            props.editContext.state = props.editContext.state.copy(
                createWorkspaceDialog = true
            )
        }
    }
    Dialog {
        open = props.editContext.state.createWorkspaceDialog ?: false
        onClose = { _, _ ->
            props.editContext.closeAllDialogs()
        }
        DialogTitle {
            +"Enter new workspace name"
        }
        DialogContent {
            TextField {
                +"New workspace name"
                variant = FormControlVariant.outlined
                onChange = { e ->
                    props.editContext.state = props.editContext.state.copy(
                        lastTextInput = e.target.asDynamic().value.toString()
                    )
                }
            }
        }
        DialogActions {
            Button {
                +"Cancel"
                color = ButtonColor.secondary
                onClick = {
                    props.editContext.closeAllDialogs()
                }
            }
            Button {
                +"Set"
                color = ButtonColor.primary
                onClick = {
                    val lastTextInput = props.editContext.state.lastTextInput
                    props.menuProps.workManager.doWork {
                        if (lastTextInput.isNullOrEmpty()) {
                            return@doWork
                        }
                        val newWorkspace = props.menuProps.service.create(
                            Space(id = "", name = lastTextInput)
                        )
                        val updatedWorkspaces = props.menuProps.workspaces?.plus(newWorkspace)
                        props.menuProps.onWorkspacesChanged?.invoke(updatedWorkspaces)
                        props.menuProps.onWorkspaceSelected?.invoke(newWorkspace.id)
                        props.editContext.closeAllDialogs()
                    }
                }
            }
        }
    }
}

val editKonstructionButton = FC<SubComponentProps> { props ->
    IconButton {
        Edit()
        onClick = {
            props.editContext.state = props.editContext.state.copy(
                editKonstructionDialog = true
            )
        }
    }
    Dialog {
        open = props.editContext.state.editKonstructionDialog ?: false
        onClose = { _, _ ->
            props.editContext.closeAllDialogs()
        }
        DialogTitle {
            +"Change konstruction name"
        }
        DialogContent {
            TextField {
                "New konstruction name"
                defaultValue = props.currentSpace?.name
                variant = FormControlVariant.outlined
                onChange = { e ->
                    props.editContext.state = props.editContext.state.copy(
                        lastTextInput = e.target.asDynamic().value.toString()
                    )
                }
            }
        }
        DialogActions {
            Button {
                +"Cancel"
                color = ButtonColor.secondary
                onClick = {
                    props.editContext.closeAllDialogs()
                }
            }
            Button {
                +"Create"
                color = ButtonColor.primary
                onClick = {
                    val lastTextInput = props.editContext.state.lastTextInput
                    props.menuProps.workManager.doWork {
                        if (lastTextInput == null || lastTextInput == props.currentSpace?.name) {
                            props.editContext.closeAllDialogs()
                            return@doWork
                        }
                        props.menuProps.currentKonstructionService?.setName(lastTextInput)
                        val updatedKonstructions = props.menuProps.konstructions?.map {
                            if (it.id == props.menuProps.currentKonstruction) {
                                it.copy(name = lastTextInput)
                            } else it
                        }
                        props.menuProps.onKonstructionsChanged?.invoke(updatedKonstructions)
                        props.editContext.closeAllDialogs()
                    }
                }
            }
        }
    }
}

val createKonstructionButton = FC<SubComponentProps> { props ->
    IconButton {
        Add()
        onClick = {
            props.editContext.state = props.editContext.state.copy(
                createKonstructionDialog = true
            )
        }
    }
    Dialog {
        open = props.editContext.state.createKonstructionDialog ?: false
        onClose = { _, _ ->
            props.editContext.closeAllDialogs()
        }
        DialogTitle {
            +"Enter new konstruction name"
        }
        DialogContent {
            TextField {
                "New konstruction name"
                variant = FormControlVariant.outlined
                onChange = { e ->
                    props.editContext.state = props.editContext.state.copy(
                        lastTextInput = e.target.asDynamic().value.toString()
                    )
                }
            }
        }
        DialogActions {
            Button {
                +"Cancel"
                color = ButtonColor.secondary
                onClick = {
                    props.editContext.closeAllDialogs()
                }
            }
            Button {
                +"Set"
                color = ButtonColor.primary
                onClick = {
                    val lastTextInput = props.editContext.state.lastTextInput
                    props.menuProps.workManager.doWork {
                        if (lastTextInput.isNullOrEmpty()) {
                            return@doWork
                        }
                        val newKonstruction = props.menuProps.currentWorkspaceService?.create(
                            Konstruction(
                                id = "",
                                name = lastTextInput,
                                workspaceId = props.menuProps.currentWorkspace
                                    ?: error("Lost workspace")
                            )
                        ) ?: error("Missing workspace")
                        val updatedKonstructions =
                            props.menuProps.konstructions?.plus(newKonstruction)
                        props.menuProps.onKonstructionsChanged?.invoke(updatedKonstructions)
                        props.menuProps.onKonstructionSelected?.invoke(newKonstruction.id)
                        props.editContext.closeAllDialogs()
                    }
                }
            }
        }
    }
}
