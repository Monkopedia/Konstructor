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
@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.konstructor

import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * On-disk layout shared by every item store: one directory per item, each holding an
 * [INFO_JSON] describing it. [KonstructorImpl] keeps [com.monkopedia.konstructor.common.Space]
 * dirs under the data dir; [WorkspaceImpl] keeps
 * [com.monkopedia.konstructor.common.KonstructionInfo] dirs under a workspace.
 *
 * These rules are load-bearing persistence behaviour, so they live in one place rather
 * than being restated per service.
 */
internal const val INFO_JSON = "info.json"

/**
 * Every immediate subdirectory of [this] that holds an [INFO_JSON], decoded as [T].
 *
 * Non-directories and directories with no [INFO_JSON] are skipped (a half-created or
 * hand-made directory must not fail the whole listing), and an unreadable parent yields
 * an empty list.
 */
internal inline fun <reified T> File.listInfo(json: Json): List<T> =
    listFiles()?.mapNotNull { child ->
        if (!child.isDirectory) return@mapNotNull null
        val infoFile = File(child, INFO_JSON)
        if (!infoFile.exists()) return@mapNotNull null
        infoFile.inputStream().use { input ->
            json.decodeFromStream<T>(input)
        }
    } ?: emptyList()

/**
 * The lowest non-negative integer, as a string, that is not in [usedIds].
 *
 * Membership is tested against a hash set, so allocating an id is linear in the number
 * of existing items rather than quadratic.
 */
internal fun firstFreeId(usedIds: Collection<String>): String {
    val used = usedIds.toHashSet()
    var id = 0
    while (used.contains(id.toString())) {
        id++
    }
    return id.toString()
}

/**
 * Claim [targetInfo] for a new item and write [value] to it.
 *
 * Both the file and its directory must be absent — an existing directory means the id is
 * taken even when its [INFO_JSON] was never written — otherwise this throws
 * [IllegalArgumentException] naming [id].
 */
internal inline fun <reified T> writeNewInfo(targetInfo: File, id: String, json: Json, value: T) {
    if (targetInfo.exists() || targetInfo.parentFile.exists()) {
        throw IllegalArgumentException("$id has been used already")
    }
    targetInfo.parentFile.mkdirs()
    targetInfo.outputStream().use { output ->
        json.encodeToStream(value, output)
    }
}
