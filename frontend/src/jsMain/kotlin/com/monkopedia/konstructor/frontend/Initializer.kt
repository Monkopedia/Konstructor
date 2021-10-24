package com.monkopedia.konstructor.frontend

import com.ccfraser.muirwik.components.MIconColor
import com.ccfraser.muirwik.components.MTypographyVariant
import com.ccfraser.muirwik.components.button.MIconButtonSize
import com.ccfraser.muirwik.components.button.mIconButton
import com.ccfraser.muirwik.components.form.MFormControlVariant
import com.ccfraser.muirwik.components.mCircularProgress
import com.ccfraser.muirwik.components.mTextField
import com.ccfraser.muirwik.components.mTypography
import com.ccfraser.muirwik.components.targetInputValue
import com.ccfraser.muirwik.components.targetValue
import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.ksrpc.KsrpcUri
import com.monkopedia.ksrpc.connect
import com.monkopedia.ksrpc.toKsrpcUri
import io.ktor.client.HttpClient
import kotlinext.js.jsObject
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.css.Align
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.JustifyContent
import kotlinx.css.LinearDimension
import kotlinx.css.alignContent
import kotlinx.css.alignItems
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.marginLeft
import kotlinx.css.marginTop
import kotlinx.css.px
import kotlinx.css.vh
import kotlinx.css.vw
import kotlinx.css.width
import org.w3c.dom.Location
import org.w3c.dom.url.URLSearchParams
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.setState
import styled.css
import styled.styledDiv

private val Location.backendUrl: String
    get() =
        (
            protocol + "//" + hostname + "${
            port?.toIntOrNull()?.let { ":$it" } ?: ""
            }" + "/konstructor"
            )

external interface InitializerState : State {
    var uri: KsrpcUri?
    var konstructor: Konstructor?
    var workspaceList: List<Space>?
    var isWorking: Boolean?
    var workManager: WorkManager
}

class Initializer : RComponent<Props, InitializerState>() {

    init {
        this.state = jsObject {
            workManager = WorkManager(this@Initializer::onWorkingChanged)
        }
    }

    private fun onWorkingChanged(working: Boolean) {
        setState {
            isWorking = working
        }
    }

    override fun RBuilder.render() {
        child(WorkDisplay::class) {
            attrs {
                isWorking = state.isWorking ?: false
            }
        }
        val uri = state.uri
        if (uri == null) {
            loading()
            GlobalScope.launch {
                val params = URLSearchParams(window.location.search.substring(1))
                val url = params.get("backend") ?: window.location.backendUrl
                console.log("Using backend from $url")
                val uri = url.toKsrpcUri()
                setState {
                    this.uri = uri
                }
            }
            return
        }
        val konstructor = state.konstructor
        if (konstructor == null) {
            loading()
            GlobalScope.launch {
                val service = Konstructor.createStub(uri.connect { HttpClient() })
                setState {
                    this.konstructor = service
                }
            }
            return
        }
        val workspaceList = state.workspaceList
        if (workspaceList == null) {
            loading()
            GlobalScope.launch {
                val list = konstructor.list(Unit)
                setState {
                    this.workspaceList = list
                }
            }
            return
        }
        if (workspaceList.isEmpty()) {
            child(CreateFirstWorkspace::class) {
                attrs {
                    this.konstructor = konstructor
                    this.onWorkspaceListChanged = this@Initializer::onWorkspaceListChanged
                }
            }
            return
        }

        child(MainScreen::class) {
            attrs {
                this.service = konstructor
                this.workspaceList = workspaceList
                this.onWorkspaceListChanged = this@Initializer::onWorkspaceListChanged
                this.workManager = state.workManager
            }
        }
    }

    private fun onWorkspaceListChanged(list: List<Space>?) {
        setState { workspaceList = list }
    }

    private fun RBuilder.loading() {
        styledDiv {
            css {
                marginLeft = LinearDimension.auto
                marginTop = LinearDimension("50px")
            }
            mTypography("Loading...", MTypographyVariant.h1)
        }
    }
}

external interface CreateFirstWorkspaceProps : Props {
    var konstructor: Konstructor
    var onWorkspaceListChanged: ((List<Space>?) -> Unit)?
}

external interface CreateFirstWorkspaceState : State {
    var textValue: String?
    var creating: Boolean?
}

class CreateFirstWorkspace : RComponent<CreateFirstWorkspaceProps, CreateFirstWorkspaceState>() {
    override fun RBuilder.render() {
        styledDiv {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                justifyContent = JustifyContent.center
                alignContent = Align.center
                width = 100.vw
                height = 100.vh
            }
            styledDiv {
                css {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    justifyContent = JustifyContent.center
                    alignContent = Align.center
                    alignItems = Align.center
                }
                if (state.creating == true) {
                    mCircularProgress(size = 80.px)
                    return@styledDiv
                }
                mTextField(
                    "First workspace name",
                    variant = MFormControlVariant.outlined,
                    onChange = { e ->
                        val value = e.targetInputValue.toString()
                        println("Latest test [$value]")
                        setState { this.textValue = value }
                    }
                )
                mIconButton(
                    "login",
                    disabled = state.textValue.isNullOrEmpty().also {
                        println("Disabling: $it (${state.textValue})")
                    },
                    size = MIconButtonSize.medium,
                    iconColor = MIconColor.primary,
                    onClick = {
                        setState { creating = true }
                        GlobalScope.launch {
                            val name = state.textValue.toString()
                            val created = props.konstructor.create(Space("", name))
                            setState { creating = false }
                            props.onWorkspaceListChanged?.invoke(listOf(created))
                        }
                    }
                )
            }
        }
    }
}
