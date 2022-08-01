package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import csstype.AlignContent
import csstype.AlignItems
import csstype.Display
import csstype.FlexDirection
import csstype.JustifyContent
import csstype.px
import csstype.vh
import emotion.react.css
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mui.icons.material.Login
import mui.material.CircularProgress
import mui.material.FormControlVariant
import mui.material.IconButton
import mui.material.IconButtonColor.primary
import mui.material.Size.medium
import mui.material.TextField
import react.FC
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.useState


external interface CreateFirstWorkspaceProps : Props {
    var konstructor: Konstructor
    var onWorkspaceListChanged: ((List<Space>?) -> Unit)?
}

data class CreateFirstWorkspaceState(
    val textValue: String? = null,
    val creating: Boolean? = null
): State

val CreateFirstWorkspace = FC<CreateFirstWorkspaceProps> { props ->
    var state by useState(CreateFirstWorkspaceState())
    div {
        css {
            display = Display.flex
            flexDirection = FlexDirection.column
            justifyContent = JustifyContent.center
            alignContent = AlignContent.center
            width = 100.vh
            height = 100.vh
        }
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                justifyContent = JustifyContent.center
                alignContent = AlignContent.center
                alignItems = AlignItems.center
            }
            if (state.creating == true) {
                CircularProgress {
                    size = 80.px
                }
                return@div
            }
            TextField {
                +"First workspace name"
                variant = FormControlVariant.outlined
                onChange = { e ->
                    val value = e.target.asDynamic().value
                    state = state.copy(textValue = value)
                }
            }
            IconButton {
                Login()
                disabled = state.textValue.isNullOrEmpty().also {
                    println("Disabling: $it (${state.textValue})")
                }
                size = medium
                color = primary
                onClick = {
                    state = state.copy(creating = true)
                    GlobalScope.launch {
                        val name = state.textValue.toString()
                        val created = props.konstructor.create(Space("", name))
                        state = state.copy(creating = false)
                        props.onWorkspaceListChanged?.invoke(listOf(created))
                    }
                }
            }
        }
    }
}