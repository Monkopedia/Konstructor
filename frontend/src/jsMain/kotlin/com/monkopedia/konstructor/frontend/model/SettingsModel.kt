package com.monkopedia.konstructor.frontend.model

import com.monkopedia.konstructor.frontend.utils.MutablePersistentFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsModel(private val scope: CoroutineScope) {
    private val mutableShowCodeLeft =
        MutablePersistentFlow.boolean("settings.showCodeLeft")
    val showCodeLeft: Flow<Boolean> = mutableShowCodeLeft

    fun setShowCodeLeft(showCodeLeft: Boolean) {
        mutableShowCodeLeft.set(showCodeLeft)
    }

    enum class CodePaneMode {
        EDITOR,
        NAVIGATION,
        GL_SETTINGS,
        RULE,
        SETTINGS
    }

    private val mutableCodePaneMode = MutableStateFlow(CodePaneMode.EDITOR)
    val codePaneMode: Flow<CodePaneMode> = mutableCodePaneMode

    fun setCodePaneMode(mode: CodePaneMode) {
        mutableCodePaneMode.value = mode
    }
}
