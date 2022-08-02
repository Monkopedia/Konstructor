package com.monkopedia.konstructor.frontend.menu

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
