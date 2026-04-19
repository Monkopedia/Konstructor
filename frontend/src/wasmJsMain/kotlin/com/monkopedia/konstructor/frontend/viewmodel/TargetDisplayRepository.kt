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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.w3c.dom.Storage

/**
 * User-controlled display settings for a single render target:
 * whether it's visible in the 3D pane and which color to tint it.
 */
@Serializable
data class TargetDisplay(
    val name: String,
    val color: String = "#ffffff",
    val isEnabled: Boolean = true
)

/**
 * Per-konstruction display settings (enabled + color) for each render target.
 * State is keyed by workspace+konstruction so different konstructions keep
 * independent settings. Settings are persisted to localStorage under
 * `konstructor.targets.{wsId}.{konId}` as JSON.
 *
 * Usage:
 *   repo.activate(workspaceId, konstructionId)  // loads saved state or empty
 *   repo.mergeTargets(listOf("cubeA", "cubeB")) // keeps saved state, adds new
 *   repo.setEnabled("cubeA", false)             // updates + persists
 *   repo.displays.collect { ... }               // observe state
 */
class TargetDisplayRepository(
    private val storage: Storage = window.localStorage,
    private val json: Json = DefaultJson
) {

    private val _displays = MutableStateFlow<Map<String, TargetDisplay>>(emptyMap())
    val displays: StateFlow<Map<String, TargetDisplay>> = _displays.asStateFlow()

    private var currentKey: String? = null

    /** Switch to the given konstruction, loading its saved state from storage. */
    fun activate(workspaceId: String, konstructionId: String) {
        val key = "$workspaceId.$konstructionId"
        currentKey = key
        _displays.value = load(key)
    }

    /** Clear state when no konstruction is active. */
    fun clear() {
        currentKey = null
        _displays.value = emptyMap()
    }

    /**
     * Reconcile the stored displays with the authoritative list of target
     * names. Adds entries for new targets (with defaults), removes entries
     * for targets that no longer exist.
     */
    fun mergeTargets(names: List<String>) {
        val current = _displays.value.toMutableMap()
        var changed = false
        for (name in names) {
            if (name !in current) {
                current[name] = TargetDisplay(name = name)
                changed = true
            }
        }
        val toRemove = current.keys - names.toSet()
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { current.remove(it) }
            changed = true
        }
        if (changed) commit(current)
    }

    fun setEnabled(name: String, enabled: Boolean) {
        update(name) { it.copy(isEnabled = enabled) }
    }

    fun setColor(name: String, color: String) {
        update(name) { it.copy(color = color) }
    }

    private inline fun update(name: String, transform: (TargetDisplay) -> TargetDisplay) {
        val current = _displays.value
        val existing = current[name] ?: TargetDisplay(name = name)
        commit(current + (name to transform(existing)))
    }

    private fun commit(newValue: Map<String, TargetDisplay>) {
        _displays.value = newValue
        val key = currentKey ?: return
        storage.setItem("$PREFIX$key", json.encodeToString(MapSerializer, newValue))
    }

    private fun load(key: String): Map<String, TargetDisplay> {
        val raw = storage.getItem("$PREFIX$key") ?: return emptyMap()
        return try {
            json.decodeFromString(MapSerializer, raw)
        } catch (_: Throwable) {
            // Corrupt/incompatible — fall back to empty in memory, leave
            // storage alone so a rollback can still recover it.
            emptyMap()
        }
    }

    companion object {
        private const val PREFIX = "konstructor.targets."
        private val DefaultJson = Json { ignoreUnknownKeys = true }
        private val MapSerializer =
            MapSerializer(String.serializer(), TargetDisplay.serializer())
    }
}
