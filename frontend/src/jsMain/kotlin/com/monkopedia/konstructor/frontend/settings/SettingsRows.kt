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

import emotion.react.css
import mui.material.FormControlLabel
import mui.material.LabelPlacement.Companion.start
import mui.material.Slider
import mui.material.SliderColor
import mui.material.Switch
import mui.material.SwitchColor.Companion.primary
import mui.material.Typography
import react.FC
import react.PropsWithClassName
import react.create
import react.dom.html.ReactHTML.div
import web.cssom.AlignItems
import web.cssom.Auto
import web.cssom.Display
import web.cssom.FlexDirection
import web.cssom.pct
import web.cssom.px

external interface SwitchRowProps : PropsWithClassName {
    var label: String
    var value: Boolean
    var onValueChanged: ((Boolean) -> Unit)
}

val SwitchRow = FC<SwitchRowProps> { props ->
    FormControlLabel {
        css {
            width = 100.pct
            height = 48.px
        }
        this.value = props.value
        this.control = Switch.create {
            this.color = primary
            this.checked = props.value
        }
        this.label = Typography.create {
            css {
                width = 100.pct
            }
            +props.label
        }
        this.labelPlacement = start
        this.onChange = { a, b ->
            props.onValueChanged(b)
        }
    }
}

external interface SliderRowProps : PropsWithClassName {
    var label: String
    var min: Int
    var max: Int
    var value: Int
    var onValueChanged: ((Int) -> Unit)
}

val SliderRow = FC<SliderRowProps> { props ->
    div {
        css {
            width = Auto.auto
        }
        Typography {
            +props.label
        }
        Slider {
            css {
                marginLeft = 16.px
            }
            this.color = SliderColor.primary
            this.value = props.value
            this.min = props.min
            this.max = props.max
            this.onChange = { a, value, _ ->
                props.onValueChanged((value as Number).toInt())
            }
        }
    }
}

external interface ButtonRowProps : PropsWithClassName {
    var label: String
    var onClick: (() -> Unit)
}

val ButtonRow = FC<ButtonRowProps> { props ->
    div {
        css {
            width = 100.pct
            height = 48.px
            paddingLeft = 16.px
            display = Display.flex
            flexDirection = FlexDirection.row
            alignItems = AlignItems.center
        }
        Typography {
            css {
                width = 100.pct
            }
            +props.label
            this.onClick = {
                props.onClick()
            }
        }
    }
}
