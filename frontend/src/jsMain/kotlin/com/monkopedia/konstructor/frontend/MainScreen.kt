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
package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.frontend.editor.KonstructionEditor
import com.monkopedia.konstructor.frontend.gl.GLScreen
import com.monkopedia.konstructor.frontend.koin.KonstructionScope
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.menu.MenuComponent
import com.monkopedia.konstructor.frontend.model.GlobalDialogsModel
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.EDITOR
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.GL_SETTINGS
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.NAVIGATION
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.RULE
import com.monkopedia.konstructor.frontend.model.SettingsModel.CodePaneMode.SETTINGS
import com.monkopedia.konstructor.frontend.settings.GlSettingsPane
import com.monkopedia.konstructor.frontend.settings.NavigationPane
import com.monkopedia.konstructor.frontend.settings.SelectModelsPane
import com.monkopedia.konstructor.frontend.settings.SettingsPane
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Display
import csstype.FlexDirection
import csstype.pct
import emotion.react.css
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.memo
import react.useMemo

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
    val globalDialogsModel: GlobalDialogsModel = useMemo(props.workManager) {
        RootScope.get { parametersOf(props.workManager) }
    }
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
        RULE -> {
            SelectModelsPane {
                this.workManager = props.workManager
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
    GlobalDialogs {
        dialogModel = globalDialogsModel
    }
}
