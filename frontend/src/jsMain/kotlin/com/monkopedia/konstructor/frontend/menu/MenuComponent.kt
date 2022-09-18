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
package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.EDITOR
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.GL_SETTINGS
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.NAVIGATION
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.RULE
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.SETTINGS
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Position.Companion.relative
import csstype.important
import csstype.number
import csstype.pct
import emotion.react.css
import mui.icons.material.ArrowBack
import mui.icons.material.LightMode
import mui.icons.material.Rule
import mui.icons.material.Settings
import mui.material.AppBar
import mui.material.IconButton
import mui.material.IconButtonColor.inherit
import mui.material.IconButtonEdge.end
import mui.material.IconButtonEdge.start
import mui.material.Size.large
import mui.material.Toolbar
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.FC
import react.Props
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML.div

external interface MenuComponentProps : Props {
    var workManager: WorkManager
}

val MenuComponent = FC<MenuComponentProps> { props ->
    val mode = RootScope.settingsModel.codePaneMode.useCollected(EDITOR)
    val spaceListModel = RootScope.spaceListModel
    val currentSpace = spaceListModel.selectedSpace.useCollected()
    val currentKonstruction = RootScope.scopeTracker.currentKonstruction.useCollected()

    AppBar {
        css {
            width = important(100.pct)
            position = important(relative)
        }
        Toolbar {
            css {
                width = important(100.pct)
            }
            if (mode != EDITOR) {
                IconButton {
                    this.size = large
                    this.edge = start
                    this.color = inherit
                    this.ariaLabel = "back"
                    this.onClick = {
                        RootScope.settingsModel.setCodePaneMode(EDITOR)
                    }
                    ArrowBack()
                }
            }

            Typography {
                this.variant = TypographyVariant.h6
                this.component = div
                this.sx {
                    this.flexGrow = number(1.0)
                }
                when (mode) {
                    RULE,
                    EDITOR -> {
                        this.onClick = {
                            RootScope.settingsModel.setCodePaneMode(NAVIGATION)
                        }
                        if (currentSpace == null) {
                            +"<No Space Selected>"
                        } else if (currentKonstruction == null) {
                            +"${currentSpace.name} > <No Konstruction Selected>"
                        } else {
                            +"${currentSpace.name} > ${currentKonstruction.name}"
                        }
                    }
                    NAVIGATION -> {
                        if (currentSpace != null && currentKonstruction != null) {
                            this.onClick = {
                                RootScope.settingsModel.setCodePaneMode(EDITOR)
                            }
                        }
                        +"Navigation"
                    }
                    GL_SETTINGS -> {
                        this.onClick = {
                            RootScope.settingsModel.setCodePaneMode(NAVIGATION)
                        }
                        +"Viewport Settings"
                    }
                    SETTINGS -> {
                        this.onClick = {
                            RootScope.settingsModel.setCodePaneMode(NAVIGATION)
                        }
                        +"Settings"
                    }
                }
            }
            IconButton {
                this.size = large
                this.edge = end
                this.color = inherit
                this.ariaLabel = "selection"
                this.onClick = {
                    if (mode != RULE) {
                        RootScope.settingsModel.setCodePaneMode(RULE)
                    } else {
                        RootScope.settingsModel.setCodePaneMode(EDITOR)
                    }
                }
                Rule()
            }
            IconButton {
                this.size = large
                this.edge = end
                this.color = inherit
                this.ariaLabel = "lighting"
                this.onClick = {
                    if (mode != GL_SETTINGS) {
                        RootScope.settingsModel.setCodePaneMode(GL_SETTINGS)
                    } else {
                        RootScope.settingsModel.setCodePaneMode(EDITOR)
                    }
                }
                LightMode()
            }
            IconButton {
                this.size = large
                this.edge = end
                this.color = inherit
                this.ariaLabel = "settings"
                this.onClick = {
                    if (mode != SETTINGS) {
                        RootScope.settingsModel.setCodePaneMode(SETTINGS)
                    } else {
                        RootScope.settingsModel.setCodePaneMode(EDITOR)
                    }
                }
                Settings()
            }
        }
    }
}
