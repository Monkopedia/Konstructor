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
package com.monkopedia.konstructor.frontend.utils

import kotlin.reflect.KProperty
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.w3c.dom.Storage as WebStorage
import org.w3c.dom.get
import org.w3c.dom.set

sealed class Storage<T>(val key: String, val storage: WebStorage) {

    inline fun get(): T {
        return parse(storage[key])
    }

    inline fun set(value: T) {
        val str = stringify(value)
        if (str != null) {
            storage[key] = str
        } else {
            storage.removeItem(key)
        }
    }

    inline operator fun getValue(
        thisRef: Nothing?,
        property: KProperty<*>
    ): T = get()

    inline operator fun setValue(
        thisRef: Nothing?,
        property: KProperty<*>,
        value: T
    ) {
        set(value)
    }

    abstract fun parse(value: String?): T
    abstract fun stringify(value: T): String?

    companion object {
        fun boolean(
            persistKey: String,
            default: Boolean = false,
            useSession: Boolean = false
        ): Storage<Boolean> =
            BooleanStorage(default, persistKey, if (useSession) sessionStorage else localStorage)

        fun optionalBoolean(
            persistKey: String,
            useSession: Boolean = false
        ): Storage<Boolean?> =
            BooleanStorage(null, persistKey, if (useSession) sessionStorage else localStorage)

        fun string(
            persistKey: String,
            default: String = "",
            useSession: Boolean = false
        ): Storage<String> =
            StringStorage(default, persistKey, if (useSession) sessionStorage else localStorage)

        fun optionalString(
            persistKey: String,
            useSession: Boolean = false
        ): Storage<String?> =
            StringStorage(null, persistKey, if (useSession) sessionStorage else localStorage)

        fun int(
            persistKey: String,
            default: Int = 0,
            useSession: Boolean = false
        ): Storage<Int> =
            IntStorage(default, persistKey, if (useSession) sessionStorage else localStorage)

        fun optionalInt(
            persistKey: String,
            useSession: Boolean = false
        ): Storage<Int?> =
            IntStorage(null, persistKey, if (useSession) sessionStorage else localStorage)

        inline fun <reified T> serialized(
            persistKey: String,
            default: T,
            useSession: Boolean = false,
            stringFormat: StringFormat = Json
        ): Storage<T> =
            SerializedStorage(
                default,
                stringFormat,
                serializer(),
                persistKey,
                if (useSession) sessionStorage else localStorage
            )

        inline fun <reified T> optionalSerialized(
            persistKey: String,
            useSession: Boolean = false,
            stringFormat: StringFormat = Json
        ): Storage<T?> =
            SerializedStorage(
                null,
                stringFormat,
                serializer(),
                persistKey,
                if (useSession) sessionStorage else localStorage
            )
    }
}

class SerializedStorage<T>(
    private val default: T,
    private val stringFormat: StringFormat,
    private val serialization: KSerializer<T>,
    key: String,
    storage: WebStorage
) : Storage<T>(key, storage) {
    @Suppress("UNCHECKED_CAST")
    override fun parse(value: String?): T =
        (value?.let { stringFormat.decodeFromString(serialization, it) } ?: default)

    override fun stringify(value: T): String? =
        value?.let { stringFormat.encodeToString(serialization, it) }
}

class StringStorage<T : String?>(private val default: T, key: String, storage: WebStorage) :
    Storage<T>(key, storage) {
    @Suppress("UNCHECKED_CAST")
    override fun parse(value: String?): T = (value ?: default) as T

    override fun stringify(value: T): String? = value
}

class BooleanStorage<T : Boolean?>(private val default: T, key: String, storage: WebStorage) :
    Storage<T>(key, storage) {
    @Suppress("UNCHECKED_CAST")
    override fun parse(value: String?): T = (value?.toBoolean() ?: default) as T

    override fun stringify(value: T): String? = value?.toString()
}

class IntStorage<T : Int?>(private val default: T, key: String, storage: WebStorage) :
    Storage<T>(key, storage) {
    @Suppress("UNCHECKED_CAST")
    override fun parse(value: String?): T = (value?.toInt() ?: default) as T

    override fun stringify(value: T): String? = value?.toString()
}
