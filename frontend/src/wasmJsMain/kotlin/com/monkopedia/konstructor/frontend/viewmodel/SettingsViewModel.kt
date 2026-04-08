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
package com.monkopedia.konstructor.frontend.viewmodel

import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CodePaneMode {
    EDITOR,
    NAVIGATION,
    SETTINGS,
    GL_SETTINGS,
    SELECTION
}

enum class EditorThemeName(val displayName: String, val isDark: Boolean) {
    DRACULA("Dracula", true),
    ONE_DARK("One Dark", true),
    GITHUB_LIGHT("GitHub Light", false),
    MATERIAL("Material (auto)", true)
}

enum class KeymapName(val displayName: String) {
    DEFAULT("Default"),
    VIM("Vim"),
    EMACS("Emacs")
}

class SettingsViewModel {

    private val storage = window.localStorage

    private val _codePaneMode = MutableStateFlow(CodePaneMode.EDITOR)
    val codePaneMode: StateFlow<CodePaneMode> = _codePaneMode.asStateFlow()

    private val _editorTheme = MutableStateFlow(loadEnum("editorTheme", EditorThemeName.DRACULA))
    val editorTheme: StateFlow<EditorThemeName> = _editorTheme.asStateFlow()

    private val _keymap = MutableStateFlow(loadEnum("keymap", KeymapName.VIM))
    val keymap: StateFlow<KeymapName> = _keymap.asStateFlow()

    private val _showCodeLeft = MutableStateFlow(loadBoolean("showCodeLeft", false))
    val showCodeLeft: StateFlow<Boolean> = _showCodeLeft.asStateFlow()

    private val _showFps = MutableStateFlow(loadBoolean("showFps", false))
    val showFps: StateFlow<Boolean> = _showFps.asStateFlow()

    private val _showCameraWidget = MutableStateFlow(loadBoolean("showCameraWidget", true))
    val showCameraWidget: StateFlow<Boolean> = _showCameraWidget.asStateFlow()

    private val _ambientLightIntensity = MutableStateFlow(loadFloat("ambientLight", 0.5f))
    val ambientLightIntensity: StateFlow<Float> = _ambientLightIntensity.asStateFlow()

    fun setCodePaneMode(mode: CodePaneMode) {
        _codePaneMode.value = mode
    }

    fun setEditorTheme(theme: EditorThemeName) {
        _editorTheme.value = theme
        saveString("editorTheme", theme.name)
    }

    fun setKeymap(keymap: KeymapName) {
        _keymap.value = keymap
        saveString("keymap", keymap.name)
    }

    fun setShowCodeLeft(value: Boolean) {
        _showCodeLeft.value = value
        saveBoolean("showCodeLeft", value)
    }

    fun setShowFps(value: Boolean) {
        _showFps.value = value
        saveBoolean("showFps", value)
    }

    fun setShowCameraWidget(value: Boolean) {
        _showCameraWidget.value = value
        saveBoolean("showCameraWidget", value)
    }

    fun setAmbientLightIntensity(value: Float) {
        _ambientLightIntensity.value = value
        saveFloat("ambientLight", value)
    }

    private fun loadBoolean(key: String, default: Boolean): Boolean {
        return storage.getItem("konstructor.$key")?.toBooleanStrictOrNull() ?: default
    }

    private fun saveBoolean(key: String, value: Boolean) {
        storage.setItem("konstructor.$key", value.toString())
    }

    private fun loadFloat(key: String, default: Float): Float {
        return storage.getItem("konstructor.$key")?.toFloatOrNull() ?: default
    }

    private fun saveFloat(key: String, value: Float) {
        storage.setItem("konstructor.$key", value.toString())
    }

    private fun saveString(key: String, value: String) {
        storage.setItem("konstructor.$key", value)
    }

    private inline fun <reified T : Enum<T>> loadEnum(key: String, default: T): T {
        val name = storage.getItem("konstructor.$key") ?: return default
        return try {
            enumValueOf<T>(name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }
}
