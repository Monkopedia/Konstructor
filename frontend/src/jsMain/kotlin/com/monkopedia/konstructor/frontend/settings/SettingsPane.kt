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

import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.theme
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Color
import csstype.Display
import csstype.FlexDirection
import csstype.pct
import csstype.px
import emotion.react.css
import mui.material.Divider
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useCallback

val SettingsPane = FC<Props> {
    val showLeft = RootScope.settingsModel.showCodeLeft.useCollected(false)
    val setShowLeft = useCallback { value: Boolean ->
        RootScope.settingsModel.setShowCodeLeft(value)
    }
    div {
        css {
            background = Color(theme.palette.background.paper)
        }
        css {
            width = 100.pct
            this.display = Display.flex
            this.flexDirection = FlexDirection.column
            paddingLeft = 16.px
            paddingTop = 32.px
            paddingRight = 40.px
            paddingBottom = 32.px
        }
        SwitchRow {
            css {
                width = 100.pct
            }
            this.label = "Show code on left"
            this.value = showLeft
            this.onValueChanged = setShowLeft
        }
        Divider()
    }
}