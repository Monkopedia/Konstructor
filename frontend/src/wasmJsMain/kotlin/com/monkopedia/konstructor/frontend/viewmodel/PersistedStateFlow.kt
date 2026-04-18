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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.w3c.dom.Storage

/**
 * A StateFlow-like wrapper whose current value is persisted to [Storage] under
 * a namespaced key. On construction, reads the stored value (or falls back to
 * [default]). On every [set], writes back to storage.
 *
 * Decode failures (corrupt/incompatible data from a prior build) fall back to
 * [default] in memory only — the stored value is left untouched so a future
 * build or rollback can still recover it. The next user-initiated [set]
 * naturally overwrites.
 */
class PersistedStateFlow<T> private constructor(
    private val storage: Storage,
    private val storageKey: String,
    initial: T,
    private val encode: (T) -> String,
    private val decode: (String) -> T
) {
    private val _flow = MutableStateFlow(initial)
    val flow: StateFlow<T> = _flow.asStateFlow()

    val value: T get() = _flow.value

    fun set(value: T) {
        _flow.value = value
        storage.setItem(storageKey, encode(value))
    }

    companion object {
        private const val PREFIX = "konstructor."

        @PublishedApi
        internal fun <T> create(
            key: String,
            default: T,
            encode: (T) -> String,
            decode: (String) -> T
        ): PersistedStateFlow<T> {
            val storage = window.localStorage
            val fullKey = "$PREFIX$key"
            val initial = try {
                storage.getItem(fullKey)?.let(decode) ?: default
            } catch (_: Throwable) {
                // Corrupt/incompatible — keep stored value, fall back in memory.
                default
            }
            return PersistedStateFlow(storage, fullKey, initial, encode, decode)
        }

        fun boolean(key: String, default: Boolean): PersistedStateFlow<Boolean> =
            create(key, default, { it.toString() }, { it.toBooleanStrictOrNull() ?: default })

        fun float(key: String, default: Float): PersistedStateFlow<Float> =
            create(key, default, { it.toString() }, { it.toFloatOrNull() ?: default })

        fun string(key: String, default: String): PersistedStateFlow<String> =
            create(key, default, { it }, { it })

        inline fun <reified T : Enum<T>> enum(key: String, default: T): PersistedStateFlow<T> =
            create(key, default, { it.name }, {
                try {
                    enumValueOf<T>(it)
                } catch (_: Throwable) {
                    default
                }
            })

        fun <T> serialized(
            key: String,
            default: T,
            serializer: KSerializer<T>,
            json: Json = DefaultJson
        ): PersistedStateFlow<T> =
            create(
                key,
                default,
                { json.encodeToString(serializer, it) },
                { json.decodeFromString(serializer, it) }
            )

        private val DefaultJson = Json { ignoreUnknownKeys = true }
    }
}
