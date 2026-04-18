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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class DirectionalLightConfig(
    val intensity: Float = 0.5f,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = -1f
)

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
    AMY("Amy", true),
    AYU_LIGHT("Ayu Light", false),
    BARF("Barf", true),
    BESPIN("Bespin", true),
    BIRDS_OF_PARADISE("Birds of Paradise", true),
    BOYS_AND_GIRLS("Boys and Girls", true),
    CLOUDS("Clouds", false),
    COBALT("Cobalt", true),
    COOL_GLOW("Cool Glow", true),
    ESPRESSO("Espresso", false),
    NOCTIS_LILAC("Noctis Lilac", false),
    ROSE_PINE_DAWN("Rosé Pine Dawn", false),
    SMOOTHY("Smoothy", false),
    SOLARIZED_LIGHT("Solarized Light", false),
    TOMORROW("Tomorrow", false),
    MATERIAL("Material (auto)", true)
}

enum class KeymapName(val displayName: String) {
    DEFAULT("Default"),
    VIM("Vim"),
    EMACS("Emacs")
}

private val DEFAULT_LIGHTS = listOf(
    DirectionalLightConfig(intensity = 0.5f, x = 1f, y = 1f, z = -1f),
    DirectionalLightConfig(intensity = 0.3f, x = -1f, y = -1f, z = 1f)
)

class SettingsViewModel {

    // codePaneMode is not persisted — resets to EDITOR each session.
    private val _codePaneMode = MutableStateFlow(CodePaneMode.EDITOR)
    val codePaneMode: StateFlow<CodePaneMode> = _codePaneMode.asStateFlow()

    private val editorThemeStore =
        PersistedStateFlow.enum("editorTheme", EditorThemeName.DRACULA)
    private val keymapStore =
        PersistedStateFlow.enum("keymap", KeymapName.VIM)
    private val showCodeLeftStore =
        PersistedStateFlow.boolean("showCodeLeft", false)
    private val showFpsStore =
        PersistedStateFlow.boolean("showFps", false)
    private val showCameraWidgetStore =
        PersistedStateFlow.boolean("showCameraWidget", true)
    private val ambientLightIntensityStore =
        PersistedStateFlow.float("ambientLight", 0.5f)
    private val directionalLightsStore =
        PersistedStateFlow.serialized(
            "lights",
            DEFAULT_LIGHTS,
            ListSerializer(DirectionalLightConfig.serializer())
        )

    val editorTheme: StateFlow<EditorThemeName> = editorThemeStore.flow
    val keymap: StateFlow<KeymapName> = keymapStore.flow
    val showCodeLeft: StateFlow<Boolean> = showCodeLeftStore.flow
    val showFps: StateFlow<Boolean> = showFpsStore.flow
    val showCameraWidget: StateFlow<Boolean> = showCameraWidgetStore.flow
    val ambientLightIntensity: StateFlow<Float> = ambientLightIntensityStore.flow
    val directionalLights: StateFlow<List<DirectionalLightConfig>> = directionalLightsStore.flow

    fun setCodePaneMode(mode: CodePaneMode) { _codePaneMode.value = mode }
    fun setEditorTheme(theme: EditorThemeName) = editorThemeStore.set(theme)
    fun setKeymap(keymap: KeymapName) = keymapStore.set(keymap)
    fun setShowCodeLeft(value: Boolean) = showCodeLeftStore.set(value)
    fun setShowFps(value: Boolean) = showFpsStore.set(value)
    fun setShowCameraWidget(value: Boolean) = showCameraWidgetStore.set(value)
    fun setAmbientLightIntensity(value: Float) = ambientLightIntensityStore.set(value)
    fun setDirectionalLights(lights: List<DirectionalLightConfig>) =
        directionalLightsStore.set(lights)
}
