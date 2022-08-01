package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstruction
import com.monkopedia.konstructor.common.Space
import csstype.px
import emotion.react.css
import mui.material.FormControl
import mui.material.MenuItem
import mui.material.Select
import mui.material.Size.small
import react.FC
import react.Props
import react.State

external interface WorkspaceSelectorProps : Props {
    var currentWorkspace: String?
    var workspaces: List<Space>?
    var onWorkspaceSelected: ((String) -> Unit)?
}

external interface WorkspaceSelectorState : State

val WorkspaceSelector = FC<WorkspaceSelectorProps> { props ->
    val workspaces = props.workspaces ?: return@FC
    if (workspaces.isNotEmpty()) {
        FormControl {
            size = small
            Select {
                css {
                    paddingTop = 4.px
                    paddingBottom = 4.px
                }
                value = props.currentWorkspace
                name = "Workspace"
                onChange = { event, child ->
                    val newValue = event.target.asDynamic().value.toString()
                    props.onWorkspaceSelected?.invoke(newValue)
                }
                for (workspace in workspaces) {
                    MenuItem {
                        +workspace.name
                        value = workspace.id
                    }
                }
            }
        }
    }
}

external interface KonstructionSelectorProps : Props {
    var currentKonstruction: String?
    var konstructions: List<Konstruction>?
    var onKonstructionSelected: ((String) -> Unit)?
}

val KonstructionSelector = FC<KonstructionSelectorProps> { props ->

    val workspaces = props.konstructions ?: return@FC
    if (workspaces.isNotEmpty()) {
        FormControl {
            size = small
            Select {
                css {
                    paddingTop = 4.px
                    paddingBottom = 4.px
                }
                value = props.currentKonstruction
                name = "Konstruction"
                onChange = { event, _ ->
                    val newValue = event.target.asDynamic().value
                    props.onKonstructionSelected?.invoke(newValue.toString())
                }
                for (workspace in workspaces) {
                    MenuItem {
                        +workspace.name
                        value = workspace.id
                    }
                }
            }
        }
    }
}
