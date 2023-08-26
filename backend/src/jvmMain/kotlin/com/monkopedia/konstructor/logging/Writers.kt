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

import com.monkopedia.hauler.DeliveryDay
import com.monkopedia.hauler.Formatter
import com.monkopedia.hauler.Palette
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.pack
import com.monkopedia.hauler.unpack
import com.monkopedia.konstructor.common.LogFormatter
import java.io.File
import java.io.PrintWriter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toCollection
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

suspend fun Shipper.writeText(file: File) {
    deliveries().registerDeliveryDay(fileDelivery(file))
}

fun fileDelivery(
    file: File,
    maxSize: Int = 500_000,
    formatter: Formatter = LogFormatter
): DeliveryDay = object : DeliveryDay {
    override suspend fun onLogs(event: Palette) {
        val oldLines = file.readLines()
        val newLines = flow {
            event.unpack().sortedBy { it.timestamp }.forEach { formatter(it) }
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
}

suspend fun Shipper.writeBinary(file: File) {
    deliveries().registerDeliveryDay(fileBinaryDelivery(file))
}

@OptIn(ExperimentalSerializationApi::class)
fun fileBinaryDelivery(
    file: File,
    maxCount: Int = 10_000,
    serializer: BinaryFormat = Cbor
): DeliveryDay = object : DeliveryDay {
    override suspend fun onLogs(event: Palette) {
        val oldPalette = serializer.decodeFromByteArray<Palette>(file.readBytes())
        val oldLines = oldPalette.unpack()
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
}
