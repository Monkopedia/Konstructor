package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.frontend.editor.KonstructionEditor
import com.monkopedia.konstructor.frontend.gl.GLScreen
import com.monkopedia.konstructor.frontend.koin.KonstructionScope
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.menu.MenuComponent
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.EDITOR
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.GL_SETTINGS
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.NAVIGATION
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.SETTINGS
import com.monkopedia.konstructor.frontend.settings.GlSettingsPane
import com.monkopedia.konstructor.frontend.settings.NavigationPane
import com.monkopedia.konstructor.frontend.settings.SettingsPane
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Display
import csstype.FlexDirection
import csstype.pct
import csstype.px
import emotion.react.css
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.memo

external interface MainScreenProps : Props {
    var workManager: WorkManager
}

external interface MainScreenPaneProps : Props {
    var workManager: WorkManager
    var konstructionScope: KonstructionScope?
}

val MainScreen = memo(
    FC<MainScreenProps> { props ->
        val konstructionScope = RootScope.scopeTracker.konstruction.useCollected()
        val showCodeLeft = RootScope.settingsModel.showCodeLeft.useCollected(false)

        div {
            css {
                this.display = Display.flex
                this.flexDirection = FlexDirection.row
            }
            div {
                css {
                    width = 50.pct
                }
                if (showCodeLeft) {
                    MainScreenContentPane {
                        this.workManager = props.workManager
                        this.konstructionScope = konstructionScope
                    }
                } else {
                    MainScreenGlPane {
                        this.workManager = props.workManager
                        this.konstructionScope = konstructionScope
                    }
                }
            }
            div {
                css {
                    width = 50.pct
                }
                if (showCodeLeft) {
                    MainScreenGlPane {
                        this.workManager = props.workManager
                        this.konstructionScope = konstructionScope
                    }
                } else {
                    MainScreenContentPane {
                        this.workManager = props.workManager
                        this.konstructionScope = konstructionScope
                    }
                }
            }
        }
    }
) { oldProps, newProps ->
    oldProps.workManager == newProps.workManager
}

val MainScreenGlPane = FC<MainScreenPaneProps> { props ->
    props.konstructionScope?.let { scope ->
        GLScreen {
            this.konstructionScope = scope
        }
    }
}

val MainScreenContentPane = FC<MainScreenPaneProps> { props ->
    div {
        css {
            this.display = Display.flex
            this.flexDirection = FlexDirection.column
        }
        MenuComponent {
            workManager = props.workManager
        }
        MainScreenCodePane {
            this.workManager = props.workManager
            this.konstructionScope = props.konstructionScope
        }
    }
}

val MainScreenCodePane = FC<MainScreenPaneProps> { props ->
    when (RootScope.settingsModel.codePaneMode.useCollected(EDITOR)) {
        EDITOR -> {
            props.konstructionScope?.let { konstructionScope ->
                KonstructionEditor {
                    konstructionModel = konstructionScope.scope.get()
                    workspaceId = konstructionModel.workspaceId
                    konstructionId = konstructionModel.konstructionId
                }
            }
        }
        NAVIGATION -> {
            NavigationPane {
                this.workManager = props.workManager
            }
        }
        GL_SETTINGS -> {
            GlSettingsPane {
            }
        }
        SETTINGS -> {
            SettingsPane {
            }
        }
    }
}
