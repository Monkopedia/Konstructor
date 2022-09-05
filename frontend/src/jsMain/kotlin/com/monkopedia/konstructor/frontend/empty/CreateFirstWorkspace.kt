package com.monkopedia.konstructor.frontend.empty

import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel
import com.monkopedia.konstructor.frontend.utils.useCloseable
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
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.onChange
import react.useState

external interface CreateFirstWorkspaceProps : Props {
    var workManager: WorkManager
}

val CreateFirstWorkspace = FC<CreateFirstWorkspaceProps> { props ->
    var textValue by useState<String>()
    var creating by useState(false)
    val navDialogModel =
        RootScope.useCloseable { get<NavigationDialogModel> { parametersOf(props.workManager) } }
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
            if (creating) {
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
                    textValue = value
                }
            }
            IconButton {
                Login()
                disabled = textValue.isNullOrEmpty().also {
                    println("Disabling: $it ($textValue)")
                }
                size = medium
                color = primary
                onClick = {
                    creating = true
                    GlobalScope.launch {
                        val name = textValue.toString()
                        navDialogModel.createWorkspace(name)
                        creating = false
                    }
                }
            }
        }
    }
}
