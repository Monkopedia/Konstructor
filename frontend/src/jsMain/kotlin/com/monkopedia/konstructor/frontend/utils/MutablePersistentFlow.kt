package com.monkopedia.konstructor.frontend.utils

import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class MutablePersistentFlow<T> private constructor(
    private val mutableFlow: MutableStateFlow<T>,
    private val storage: Storage<T>
) : Flow<T> by mutableFlow {

    constructor(storage: Storage<T>) : this(
        MutableStateFlow(storage.get()),
        storage
    )

    fun set(value: T) {
        storage.set(value)
        mutableFlow.value = value
    }

    companion object {

        fun boolean(
            persistKey: String,
            default: Boolean = false,
            useSession: Boolean = false
        ): MutablePersistentFlow<Boolean> =
            MutablePersistentFlow(Storage.boolean(persistKey, default, useSession))

        fun optionalBoolean(
            persistKey: String,
            useSession: Boolean = false
        ): MutablePersistentFlow<Boolean?> =
            MutablePersistentFlow(Storage.optionalBoolean(persistKey, useSession))

        fun string(
            persistKey: String,
            default: String = "",
            useSession: Boolean = false
        ): MutablePersistentFlow<String> =
            MutablePersistentFlow(Storage.string(persistKey, default, useSession))

        fun optionalString(
            persistKey: String,
            useSession: Boolean = false
        ): MutablePersistentFlow<String?> =
            MutablePersistentFlow(Storage.optionalString(persistKey, useSession))

        fun int(
            persistKey: String,
            default: Int = 0,
            useSession: Boolean = false
        ): MutablePersistentFlow<Int> =
            MutablePersistentFlow(Storage.int(persistKey, default, useSession))

        fun optionalInt(
            persistKey: String,
            useSession: Boolean = false
        ): MutablePersistentFlow<Int?> =
            MutablePersistentFlow(Storage.optionalInt(persistKey, useSession))

        inline fun <reified T> serialized(
            persistKey: String,
            default: T,
            useSession: Boolean = false,
            stringFormat: StringFormat = Json
        ): MutablePersistentFlow<T> =
            MutablePersistentFlow(Storage.serialized(persistKey, default, useSession, stringFormat))

        inline fun <reified T> optionalSerialized(
            persistKey: String,
            useSession: Boolean = false,
            stringFormat: StringFormat = Json
        ): MutablePersistentFlow<T?> =
            MutablePersistentFlow(Storage.optionalSerialized(persistKey, useSession, stringFormat))
    }
}
