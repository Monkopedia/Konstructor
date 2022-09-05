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
import org.w3c.dom.HTMLDivElement
import react.FC
import react.Props
import react.dom.html.HTMLAttributes
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
        div { dividerStyle() }
        SwitchRow {
            css {
                width = 100.pct
            }
            this.label = "Show code on left"
            this.value = false
            this.onValueChanged = {
                println("onValueChanged $it")
            }
        }
    }
}

private fun HTMLAttributes<HTMLDivElement>.dividerStyle() {
    css {
        width = 100.pct
        height = 1.px
        background = Color(theme.palette.background.paper)
        marginLeft = 16.px
        marginRight = 16.px
    }
}
