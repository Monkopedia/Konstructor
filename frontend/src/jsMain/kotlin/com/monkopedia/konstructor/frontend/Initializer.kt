package com.monkopedia.konstructor.frontend

import com.monkopedia.konstructor.frontend.empty.CreateFirstWorkspace
import com.monkopedia.konstructor.frontend.koin.RootScope
import com.monkopedia.konstructor.frontend.utils.useCollected
import csstype.Auto
import csstype.px
import emotion.react.css
import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useState

data class InitializerState(
    val workManager: WorkManager,
    val isWorking: Boolean = false
)

val Loading = FC<Props> {
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
    var state by useState(
        InitializerState(
            workManager = WorkManager()
        )
    )

    fun onWorkingChanged(working: Boolean) {
        state = state.copy(isWorking = working)
    }
    state.workManager.onWorkingChanged = ::onWorkingChanged

    val workspaceListProp = RootScope.spaceListModel.availableWorkspaces.useCollected()
    val workspaceList = workspaceListProp

    if (workspaceList == null) {
        Loading()
        return@FC
    }

    if (workspaceList.isEmpty()) {
        CreateFirstWorkspace()
    } else {
        MainScreen {
            this.workManager = state.workManager
        }
    }
    WorkDisplay {
        isWorking = state.isWorking
    }
}
