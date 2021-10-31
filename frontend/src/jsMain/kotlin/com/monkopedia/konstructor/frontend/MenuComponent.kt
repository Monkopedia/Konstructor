package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.MColor
import com.ccfraser.muirwik.components.button.mButton
import com.ccfraser.muirwik.components.button.mIconButton
import com.ccfraser.muirwik.components.dialog.mDialog
import com.ccfraser.muirwik.components.dialog.mDialogActions
import com.ccfraser.muirwik.components.dialog.mDialogContent
import com.ccfraser.muirwik.components.dialog.mDialogTitle
import com.ccfraser.muirwik.components.form.MFormControlVariant
import com.ccfraser.muirwik.components.mAppBar
import com.ccfraser.muirwik.components.mTextField
import com.ccfraser.muirwik.components.targetInputValue
import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.KonstructionService
import com.monkopedia.konstructor.common.KonstructionType
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.common.Workspace
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.JustifyContent
import kotlinx.css.alignContent
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.justifyContent
import kotlinx.css.justifyItems
import kotlinx.css.margin
import kotlinx.css.px
import kotlinx.html.DIV
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.dom.RDOMBuilder
import react.dom.div
import react.setState
import styled.css
import styled.styledDiv

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

external interface MenuComponentState : State {
    var editWorkspaceDialog: Boolean?
    var createWorkspaceDialog: Boolean?
    var editKonstructionDialog: Boolean?
    var createKonstructionDialog: Boolean?
    var lastTextInput: String?
}

class MenuComponent : RComponent<MenuComponentProps, MenuComponentState>() {

    override fun RBuilder.render() {
        val currentSpace = props.workspaces?.find { it.id == props.currentWorkspace }
        val currentKonstruction = props.konstructions?.find { it.id == props.currentKonstruction }
        mAppBar {
            attrs {
                this.css = "width: 50% !important"
            }
            styledDiv {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    justifyContent = JustifyContent.spaceBetween
                    this.margin(vertical = 8.px, horizontal = 16.px)
                }
                div {
                    child(WorkspaceSelector::class) {
                        attrs {
                            currentWorkspace = props.currentWorkspace
                            workspaces = props.workspaces
                            onWorkspaceSelected = props.onWorkspaceSelected
                        }
                    }
                    if (currentSpace != null) {
                        editWorkspaceButton(currentSpace)
                    }
                    createWorkspaceButton()
                }
                div {
                    if (currentSpace != null) {
                        if (props.konstructions?.isNotEmpty() == true) {
                            child(KonstructionSelector::class) {
                                attrs {
                                    this.currentKonstruction = props.currentKonstruction
                                    this.konstructions = props.konstructions
                                    this.onKonstructionSelected = props.onKonstructionSelected
                                }
                            }
                        }
                        if (currentKonstruction != null) {
                            editKonstructionButton(currentKonstruction)
                        }
                        createKonstructionButton()
                    }
                }
            }
        }
    }

    private fun RDOMBuilder<DIV>.editWorkspaceButton(currentSpace: Space?) {
        mIconButton(
            "edit",
            onClick = {
                setState {
                    state.editWorkspaceDialog = true
                }
            }
        )
        mDialog(
            open = state.editWorkspaceDialog ?: false,
            onClose = { _, _ ->
                closeAllDialogs()
            }
        ) {
            mDialogTitle("Change workspace name")
            mDialogContent {
                mTextField(
                    "New workspace name",
                    defaultValue = currentSpace?.name,
                    variant = MFormControlVariant.outlined,
                    onChange = { e ->
                        setState {
                            lastTextInput = e.targetInputValue
                        }
                    }
                )
            }
            mDialogActions {
                mButton("Cancel", color = MColor.default, onClick = {
                    closeAllDialogs()
                })
                mButton("Create", color = MColor.primary, onClick = {
                    val lastTextInput = state.lastTextInput
                    props.workManager.doWork {
                        if (lastTextInput == null || lastTextInput == currentSpace?.name) {
                            closeAllDialogs()
                            return@doWork
                        }
                        props.currentWorkspaceService?.setName(lastTextInput)
                        val updatedWorkspaces = props.workspaces?.map {
                            if (it.id == props.currentWorkspace) {
                                it.copy(name = lastTextInput)
                            } else it
                        }
                        props.onWorkspacesChanged?.invoke(updatedWorkspaces)
                        closeAllDialogs()
                    }
                })
            }
        }
    }

    private fun RDOMBuilder<DIV>.createWorkspaceButton() {
        mIconButton(
            "add",
            onClick = {
                setState {
                    state.createWorkspaceDialog = true
                }
            }
        )
        mDialog(
            open = state.createWorkspaceDialog ?: false,
            onClose = { _, _ ->
                closeAllDialogs()
            }
        ) {
            mDialogTitle("Enter new workspace name")
            mDialogContent {
                mTextField(
                    "New workspace name",
                    variant = MFormControlVariant.outlined,
                    onChange = { e ->
                        setState {
                            lastTextInput = e.targetInputValue
                        }
                    }
                )
            }
            mDialogActions {
                mButton("Cancel", color = MColor.default, onClick = {
                    closeAllDialogs()
                })
                mButton("Set", color = MColor.primary, onClick = {
                    val lastTextInput = state.lastTextInput
                    props.workManager.doWork {
                        if (lastTextInput.isNullOrEmpty()) {
                            return@doWork
                        }
                        val newWorkspace = props.service.create(
                            Space(id = "", name = lastTextInput)
                        )
                        val updatedWorkspaces = props.workspaces?.plus(newWorkspace)
                        props.onWorkspacesChanged?.invoke(updatedWorkspaces)
                        props.onWorkspaceSelected?.invoke(newWorkspace.id)
                        closeAllDialogs()
                    }
                })
            }
        }
    }

    private fun RDOMBuilder<DIV>.editKonstructionButton(currentSpace: Konstruction?) {
        mIconButton(
            "edit",
            onClick = {
                setState {
                    state.editKonstructionDialog = true
                }
            }
        )
        mDialog(
            open = state.editKonstructionDialog ?: false,
            onClose = { _, _ ->
                closeAllDialogs()
            }
        ) {
            mDialogTitle("Change konstruction name")
            mDialogContent {
                mTextField(
                    "New konstruction name",
                    defaultValue = currentSpace?.name,
                    variant = MFormControlVariant.outlined,
                    onChange = { e ->
                        setState {
                            lastTextInput = e.targetInputValue
                        }
                    }
                )
            }
            mDialogActions {
                mButton("Cancel", color = MColor.default, onClick = {
                    closeAllDialogs()
                })
                mButton("Create", color = MColor.primary, onClick = {
                    val lastTextInput = state.lastTextInput
                    props.workManager.doWork {
                        if (lastTextInput == null || lastTextInput == currentSpace?.name) {
                            closeAllDialogs()
                            return@doWork
                        }
                        props.currentKonstructionService?.setName(lastTextInput)
                        val updatedKonstructions = props.konstructions?.map {
                            if (it.id == props.currentKonstruction) {
                                it.copy(name = lastTextInput)
                            } else it
                        }
                        props.onKonstructionsChanged?.invoke(updatedKonstructions)
                        closeAllDialogs()
                    }
                })
            }
        }
    }

    private fun RDOMBuilder<DIV>.createKonstructionButton() {
        mIconButton(
            "add",
            onClick = {
                setState {
                    state.createKonstructionDialog = true
                }
            }
        )
        mDialog(
            open = state.createKonstructionDialog ?: false,
            onClose = { _, _ ->
                closeAllDialogs()
            }
        ) {
            mDialogTitle("Enter new konstruction name")
            mDialogContent {
                mTextField(
                    "New konstruction name",
                    variant = MFormControlVariant.outlined,
                    onChange = { e ->
                        setState {
                            lastTextInput = e.targetInputValue
                        }
                    }
                )
            }
            mDialogActions {
                mButton("Cancel", color = MColor.default, onClick = {
                    closeAllDialogs()
                })
                mButton("Set", color = MColor.primary, onClick = {
                    val lastTextInput = state.lastTextInput
                    props.workManager.doWork {
                        if (lastTextInput.isNullOrEmpty()) {
                            return@doWork
                        }
                        val newKonstruction = props.currentWorkspaceService?.create(
                            Konstruction(
                                id = "",
                                name = lastTextInput,
                                workspaceId = props.currentWorkspace ?: error("Lost workspace")
                            )
                        ) ?: error("Missing workspace")
                        val updatedKonstructions = props.konstructions?.plus(newKonstruction)
                        props.onKonstructionsChanged?.invoke(updatedKonstructions)
                        props.onKonstructionSelected?.invoke(newKonstruction.id)
                        closeAllDialogs()
                    }
                })
            }
        }
    }

    private fun closeAllDialogs() {
        setState {
            editWorkspaceDialog = false
            createWorkspaceDialog = false
            editKonstructionDialog = false
            createKonstructionDialog = false
            lastTextInput = null
        }
    }
}
