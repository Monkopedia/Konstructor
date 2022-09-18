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
