package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.mSelect
import com.ccfraser.muirwik.components.menu.mMenuItem
import com.ccfraser.muirwik.components.targetValue
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import react.Props
import react.RBuilder
import react.RComponent
import react.State

external interface WorkspaceSelectorProps : Props {
    var currentWorkspace: String?
    var workspaces: List<Space>?
    var onWorkspaceSelected: ((String) -> Unit)?
}

external interface WorkspaceSelectorState : State {
}

class WorkspaceSelector : RComponent<WorkspaceSelectorProps, WorkspaceSelectorState>() {
    override fun RBuilder.render() {
        val workspaces = props.workspaces ?: return
        if (workspaces.isNotEmpty()) {
            mSelect(
                value = props.currentWorkspace,
                name = "Workspace",
                onChange = { event, child ->
                    val newValue = event.targetValue
                    props.onWorkspaceSelected?.invoke(newValue.toString())
                }
            ) {
                for (workspace in workspaces) {
                    mMenuItem(primaryText = workspace.name, value = workspace.id)
                }
            }
        }
    }
}
