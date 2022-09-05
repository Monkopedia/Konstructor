package com.monkopedia.konstructor.frontend.menu

import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.EDITOR
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.GL_SETTINGS
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.NAVIGATION
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.SETTINGS
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Position.Companion.relative
import csstype.important
import csstype.number
import csstype.pct
import emotion.react.css
import mui.icons.material.ArrowBack
import mui.icons.material.Menu
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
import org.koin.core.component.get
import react.FC
import react.Props
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h6

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
                this.ariaLabel = "menu"
                this.onClick = {
                    if (mode != GL_SETTINGS) {
                        RootScope.settingsModel.setCodePaneMode(GL_SETTINGS)
                    } else {
                        RootScope.settingsModel.setCodePaneMode(EDITOR)
                    }
                }
                Menu()
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
