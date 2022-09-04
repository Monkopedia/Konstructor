package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Display
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.important
import csstype.pct
import csstype.px
import emotion.react.css
import mui.material.AppBar
import org.koin.core.component.get
import react.FC
import react.Props
import react.dom.html.ReactHTML.div

external interface MenuComponentProps : Props {
    var workManager: WorkManager
}

val MenuComponent = FC<MenuComponentProps> { props ->
    val spaceListModel = RootScope.spaceListModel
    val currentId = spaceListModel.selectedSpaceId.useCollected()
    val currentSpace = spaceListModel.selectedSpace.useCollected()
    val spaces = spaceListModel.availableWorkspaces.useCollected()
    val workspaceScope = RootScope.scopeTracker.workspace.useCollected()

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
                    currentWorkspace = currentId
                    workspaces = spaces
                    onWorkspaceSelected =
                        spaceListModel.onSelectedSpace
                }
                if (currentSpace != null) {
                    editWorkspaceButton {
                        this.workManager = props.workManager
                        this.currentName = currentSpace!!.name
                        this.onUpdateName = spaceListModel.onUpdateWorkspaceName
                    }
                }
                createWorkspaceButton {
                    this.workManager = props.workManager
                    this.onCreateWorkspace = spaceListModel.onCreateWorkspace
                }
            }
            div {
                if (workspaceScope != null) {
                    KonstructionMenu {
                        this.workspaceModel = workspaceScope!!.get()
                    }
                }
            }
        }
    }
}

