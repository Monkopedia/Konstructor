package com.monkopedia.konstructor.frontend.settings

import csstype.Auto.auto
import csstype.pct
import csstype.px
import emotion.react.css
import mui.material.FormControlLabel
import mui.material.LabelPlacement.start
import mui.material.Slider
import mui.material.SliderColor
import mui.material.Switch
import mui.material.SwitchColor.primary
import mui.material.Typography
import react.FC
import react.PropsWithClassName
import react.create
import react.dom.html.ReactHTML.div

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
            width = auto
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
