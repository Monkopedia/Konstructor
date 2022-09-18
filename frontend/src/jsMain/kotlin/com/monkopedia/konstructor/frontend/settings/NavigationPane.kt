/*
 * Copyright 2022 Jason Monk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.konstructor.frontend.settings

import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.koin.WorkspaceScope
import com.monkopedia.konstructor.frontend.menu.DialogMenus
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.EDITOR
import com.monkopedia.konstructor.frontend.model.WorkspaceModel
import com.monkopedia.konstructor.frontend.utils.useCloseable
import com.monkopedia.konstructor.frontend.utils.useCollected
import com.monkopedia.konstructor.frontend.utils.useSubScope
import csstype.px
import emotion.react.css
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import mui.icons.material.Add
import mui.icons.material.Construction
import mui.icons.material.Edit
import mui.icons.material.ExpandLess
import mui.icons.material.ExpandMore
import mui.icons.material.Folder
import mui.material.Collapse
import mui.material.IconButton
import mui.material.IconButtonEdge.end
import mui.material.ListItem
import mui.material.ListItemButton
import mui.material.ListItemIcon
import mui.material.ListItemSecondaryAction
import mui.material.ListItemText
import mui.material.Typography
import mui.system.sx
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import react.FC
import react.Props
import react.create
import react.dom.aria.ariaLabel
import react.memo
import react.useState

external interface NavigationPaneProps : Props {
    var workManager: WorkManager
}

val NavigationPane = memo(
    FC<NavigationPaneProps> { props ->
        val dialogModel = RootScope.useCloseable {
            get<NavigationDialogModel> { parametersOf(props.workManager) }
        }
        val workspaces = RootScope.spaceListModel.availableWorkspaces.useCollected(emptyList())
        val selectedWorkspace = RootScope.scopeTracker.workspace.useCollected()
        DialogMenus {
            this.dialogModel = dialogModel
        }
        mui.material.List {
            for (workspace in workspaces) {
                WorkspaceListItem {
                    this.dialogModel = dialogModel
                    this.workspace = workspace
                    this.existingScope =
                        if (workspace.id == selectedWorkspace?.workspaceId) selectedWorkspace
                        else null
                }
            }
            ListItem {
                ListItemButton {
                    ListItemIcon {
                        Add()
                    }
                    ListItemText {
                        this.primary = Typography.create {
                            +"Add new space"
                        }
                        onClick = {
                            dialogModel.showCreateWorkspace()
                        }
                    }
                }
            }
        }
    }
) { _, _ ->
    true
}

external interface WorkspaceListItemProps : Props {
    var workspace: Space
    var existingScope: WorkspaceScope?
    var dialogModel: NavigationDialogModel
}

val WorkspaceListItem = memo(
    FC<WorkspaceListItemProps> { props ->
        val scope = props.existingScope
            ?: RootScope.useSubScope { get { parametersOf(props.workspace.id) } }
        val konstructions = scope.useCollected(emptyList()) {
            get<WorkspaceModel>().availableKonstructions
        }
        var isExpanded by useState(props.existingScope != null)
        ListItem {
            ListItemButton {
                ListItemIcon {
                    Folder()
                }
                ListItemText {
                    this.primary = Typography.create {
                        +props.workspace.name
                    }
                    this.onClick = {
                        isExpanded = !isExpanded
                    }
                }
                if (isExpanded) {
                    ExpandLess()
                } else {
                    ExpandMore()
                }
            }
            ListItemSecondaryAction {
                IconButton {
                    edge = end
                    ariaLabel = "rename"
                    Edit()
                    onClick = {
                        props.dialogModel.showEditWorkspace(
                            props.workspace.id,
                            props.workspace.name
                        )
                    }
                }
            }
        }
        Collapse {
            this.`in` = isExpanded
            this.timeout = "auto"
            mui.material.List {
                for (konstruction in konstructions) {
                    ListItem {
                        css {
                            paddingLeft = 72.px
                        }
                        ListItemButton {
                            ListItemIcon {
                                Construction()
                            }
                            sx {
                                this.asDynamic().pl = 8
                            }
                            ListItemText {
                                this.primary = Typography.create {
                                    +konstruction.name
                                }
                            }
                            onClick = {
                                props.dialogModel.workManager.doWork {
                                    RootScope.spaceListModel.setSelectedSpace(
                                        konstruction.workspaceId
                                    )
                                    val workspaceScope = RootScope.scopeTracker.workspace.filter {
                                        it?.workspaceId == konstruction.workspaceId
                                    }.first()!!
                                    workspaceScope.get<WorkspaceModel>()
                                        .setSelectedKonstruction(konstruction.id)
                                    RootScope.settingsModel.setCodePaneMode(EDITOR)
                                }
                            }
                        }
                        ListItemSecondaryAction {
                            IconButton {
                                edge = end
                                ariaLabel = "rename"
                                Edit()
                                onClick = {
                                    props.dialogModel.showEditKonstruction(
                                        konstruction.workspaceId,
                                        konstruction.id,
                                        konstruction.name
                                    )
                                }
                            }
                        }
                    }
                }
                ListItem {
                    css {
                        paddingLeft = 72.px
                    }
                    ListItemButton {
                        ListItemIcon {
                            Add()
                        }
                        sx {
                            this.asDynamic().pl = 8
                        }
                        ListItemText {
                            this.primary = Typography.create {
                                +"Add new konstruction"
                            }
                        }
                        onClick = {
                            props.dialogModel.showCreateKonstruction(props.workspace.id)
                        }
                    }
                }
            }
        }
    }
) { oldProps, newProps ->
    oldProps.workspace == newProps.workspace
}
