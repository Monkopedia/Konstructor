package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.common.Konstructor
import com.monkopedia.konstructor.common.Space
import com.monkopedia.konstructor.frontend.empty.CreateFirstWorkspace
import com.monkopedia.ksrpc.KsrpcUri
import com.monkopedia.ksrpc.connect
import com.monkopedia.ksrpc.ksrpcEnvironment
import com.monkopedia.ksrpc.toKsrpcUri
import com.monkopedia.ksrpc.toStub
import csstype.Auto
import csstype.px
import emotion.react.css
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mui.material.Typography
import mui.material.styles.TypographyVariant
import org.w3c.dom.Location
import org.w3c.dom.url.URLSearchParams
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useState

private val Location.backendUrl: String
    get() =
        (
            "ws://" + hostname + "${
            port.toIntOrNull()?.let { ":$it" } ?: ""
            }" + "/konstructor"
            )

data class InitializerState(
    val workManager: WorkManager,
    val uri: KsrpcUri? = null,
    val konstructor: Konstructor? = null,
    val workspaceList: List<Space>? = null,
    val isWorking: Boolean = false
)

val Loading = FC<Props> { _ ->
    div {
        css {
            marginLeft = Auto.auto
            marginTop = 50.px
        }
        Typography {
            +"Loading..."
            variant = TypographyVariant.h1
        }
    }
}

val Initializer = FC<Props> {
    val mainScreenState = useState(MainScreenState())
    var state by useState(
        InitializerState(
            workManager = WorkManager()
        )
    )

    fun onWorkingChanged(working: Boolean) {
        state = state.copy(isWorking = working)
    }
    state.workManager.onWorkingChanged = ::onWorkingChanged

    fun onWorkspaceListChanged(list: List<Space>?) {
        state = state.copy(workspaceList = list)
    }

    WorkDisplay {
        isWorking = state.isWorking ?: false
    }
    val uri = state.uri
    if (uri == null) {
        Loading()
        GlobalScope.launch {
            val params = URLSearchParams(window.location.search.substring(1))
            val url = params.get("backend") ?: window.location.backendUrl
            console.log("Using backend from $url")
            val uri = url.toKsrpcUri()
            state = state.copy(uri = uri)
        }
        return@FC
    }
    val konstructor = state.konstructor
    if (konstructor == null) {
        Loading()
        GlobalScope.launch {
            val service = uri.connect(ksrpcEnvironment { }) {
                HttpClient {
                    install(WebSockets)
                }
            }.defaultChannel()
                .toStub<Konstructor>()
            state = state.copy(konstructor = service)
        }
        return@FC
    }
    val workspaceList = state.workspaceList
    if (workspaceList == null) {
        Loading()
        GlobalScope.launch {
            val list = konstructor.list()
            state = state.copy(workspaceList = list)
        }
        return@FC
    }
    if (workspaceList.isEmpty()) {
        CreateFirstWorkspace {
            this.konstructor = konstructor
            this.onWorkspaceListChanged = ::onWorkspaceListChanged
        }
        return@FC
    }

    MainScreen {
        this.service = konstructor
        this.workspaceList = workspaceList
        this.onWorkspaceListChanged = ::onWorkspaceListChanged
        this.workManager = state.workManager
        this.mainScreenState = StateContext(mainScreenState)
    }
}
