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
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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
 * Every immediate subdirectory name under [this], i.e. every id that is TAKEN.
 *
 * Deliberately based on directory names rather than on successfully decoded [INFO_JSON]
 * files. A directory whose info has not been written yet — because a concurrent create is
 * mid-flight, or because [com.monkopedia.konstructor.PathController] created it eagerly on
 * a read path — still owns its id. Deriving free ids from decoded info instead re-offered
 * those ids forever: [listInfo] skipped them, so they never looked used, while the claim
 * below always rejected them. See konstructor#102.
 */
internal fun File.usedIds(): Collection<String> =
    listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

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
 * Attempts to claim [id] under [parent], atomically.
 *
 * `mkdir` (single, not `mkdirs`) is the claim primitive because on POSIX exactly one
 * concurrent caller can create a given directory — everyone else gets `false`. The
 * previous `exists()`-then-`mkdirs()` pair was a TOCTOU window wide enough that eight
 * concurrent creates produced five records with zero exceptions (konstructor#102).
 *
 * Returns the claimed directory, or `null` if someone else already owns the id.
 */
internal fun tryClaimId(parent: File, id: String): File? {
    parent.mkdirs()
    val dir = File(parent, id)
    return if (dir.mkdir()) dir else null
}

/**
 * Create a new item under [parent] and write its [INFO_JSON], claiming the id atomically.
 *
 * If [requestedId] is non-empty the caller chose it, and losing the claim is an ERROR —
 * they asked for a specific id and it is taken, which is exactly what
 * [IllegalArgumentException] should tell them. If it is empty the id is ours to pick, so
 * losing a race is not a failure: re-derive the lowest free id and try again. Retrying a
 * caller-supplied id would silently hand them a different object than they asked for.
 *
 * [buildValue] receives the id that was actually claimed, so callers can stamp it into the
 * payload they persist.
 */
internal inline fun <reified T> createWithClaimedId(
    parent: File,
    requestedId: String,
    json: Json,
    buildValue: (String) -> T
): String {
    if (requestedId.isNotEmpty()) {
        val dir = tryClaimId(parent, requestedId)
            ?: throw IllegalArgumentException("$requestedId has been used already")
        writeInfo(File(dir, INFO_JSON), json, buildValue(requestedId))
        return requestedId
    }
    repeat(ID_CLAIM_ATTEMPTS) {
        val candidate = firstFreeId(parent.usedIds())
        val dir = tryClaimId(parent, candidate)
        if (dir != null) {
            writeInfo(File(dir, INFO_JSON), json, buildValue(candidate))
            return candidate
        }
        // Lost the race for this id. `usedIds` reads directory names, so the winner's
        // claim is already visible and the next pass picks a different candidate — this
        // converges rather than spinning on the same number.
    }
    // Loud, not silent: callContext logs it and the caller sees the failure. Exhaustion
    // is reachable under sustained concurrent creation (measured: ~1% at 4 concurrent
    // writers, 6% at 8) — NOT, as an earlier version of this comment claimed, bounded by
    // "N creators lose at most N-1 times". That holds only for a single burst.
    throw IllegalStateException(
        "Could not claim a free id under ${parent.absolutePath} after $ID_CLAIM_ATTEMPTS " +
            "attempts; something is creating items faster than ids can be allocated."
    )
}

/** How many times to re-derive a free id when losing a concurrent claim. */
internal const val ID_CLAIM_ATTEMPTS = 32

/**
 * Write [value] to [targetInfo] so a concurrent reader never sees it half-written.
 *
 * `outputStream()` TRUNCATES on open, so writing in place leaves a window where the file
 * exists but is incomplete — and [listInfo] decodes every entry, so one torn file fails
 * the WHOLE listing. Measured on the in-place version: 18 of 1167 concurrent listings
 * failed (1.5%), rising to 15% with larger payloads, with errors like
 * `JsonDecodingException: Expected colon ':', but had 'EOF' instead at path: $.id`.
 *
 * Writing to a sibling temp file and moving it into place with [ATOMIC_MOVE] closes that:
 * a reader sees either the previous file or the complete new one, never a prefix. The temp
 * file is a sibling so the move stays within one filesystem, which is what makes it atomic.
 */
internal inline fun <reified T> writeInfo(targetInfo: File, json: Json, value: T) {
    // The temp name must be UNIQUE per write, and it must NOT be deleted on success.
    // A fixed name plus `finally { delete() }` made two concurrent writers to the same
    // info.json collide: writer A's cleanup unlinked writer B's in-flight temp, and
    // 628 of 1200 writes threw NoSuchFileException on the move. No corruption — the
    // target always decoded — but a rename that used to succeed started failing.
    val tmp = File.createTempFile("${targetInfo.name}.", ".tmp", targetInfo.parentFile)
    var moved = false
    try {
        tmp.outputStream().use { output ->
            json.encodeToStream(value, output)
        }
        Files.move(tmp.toPath(), targetInfo.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        moved = true
    } finally {
        // On success the temp no longer exists under this name; deleting unconditionally
        // is what let one writer reach into another's.
        if (!moved) tmp.delete()
    }
}
