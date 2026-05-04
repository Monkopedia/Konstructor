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
package com.monkopedia.konstructor.logging

import com.monkopedia.hauler.Box
import com.monkopedia.hauler.Palette
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.pack
import com.monkopedia.hauler.unpack
import com.monkopedia.konstructor.common.LogFormatter
import java.io.File
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toCollection
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

suspend fun Shipper.writeText(
    file: File,
    scope: CoroutineScope,
    maxSize: Int = 500_000,
    formatter: suspend FlowCollector<String>.(Box) -> Unit = LogFormatter
) {
    deliveries().streamDeliveriesPacked()
        .onEach { event -> appendText(file, event, maxSize, formatter) }
        .launchIn(scope)
}

private suspend fun appendText(
    file: File,
    event: Palette,
    maxSize: Int,
    formatter: suspend FlowCollector<String>.(Box) -> Unit
) {
    val oldLines = if (file.exists()) file.readLines() else emptyList()
    val newLines = flow {
        event.unpack().sortedBy { it.timestamp }.forEach { formatter.invoke(this, it) }
    }.toCollection(mutableListOf())

    val lines = sequence {
        newLines.asReversed().forEach { yield(it) }
        oldLines.asReversed().forEach { yield(it) }
    }
    var count = 0
    val tmpFile = File(file.parentFile, file.name + ".tmp")
    val output = PrintWriter(tmpFile.outputStream().bufferedWriter())
    lines.takeWhile {
        count += (it.length + 1)
        count < maxSize
    }.toList().asReversed().forEach {
        output.println(it)
    }
    output.flush()
    output.close()
    tmpFile.renameTo(file)
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun Shipper.writeBinary(
    file: File,
    scope: CoroutineScope,
    maxCount: Int = 10_000,
    serializer: BinaryFormat = Cbor
) {
    deliveries().streamDeliveriesPacked()
        .onEach { event -> appendBinary(file, event, maxCount, serializer) }
        .launchIn(scope)
}

@OptIn(ExperimentalSerializationApi::class)
private fun appendBinary(file: File, event: Palette, maxCount: Int, serializer: BinaryFormat) {
    val oldLines = if (file.exists()) {
        serializer.decodeFromByteArray<Palette>(file.readBytes()).unpack()
    } else {
        emptyList()
    }
    val newLines = event.unpack()
    val logs = (
        oldLines.subList(
            (oldLines.size - (maxCount - newLines.size)).coerceAtLeast(0),
            oldLines.size
        ) + newLines
        ).sortedBy { it.timestamp }
    val tmpFile = File(file.parentFile, file.name + ".tmp")
    val output = serializer.encodeToByteArray(logs.pack())
    tmpFile.writeBytes(output)
    tmpFile.renameTo(file)
}
