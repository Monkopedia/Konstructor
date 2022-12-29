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
package com.monkopedia.konstructor.lib

import com.monkopedia.hauler.DeliveryRates
import com.monkopedia.konstructor.lib.LoggingLevel.DEFAULT
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

enum class LoggingLevel {
    DEFAULT
}

@Serializable
data class ScriptConfiguration(
    val loggingLevel: LoggingLevel = DEFAULT,
    val outputDirectory: String,
    val eagerExport: Boolean = false,
    val loggingDeliveryRates: SerializableDeliveryRates = SerializableDeliveryRates.from(
        DeliveryRates()
    )
)

@Serializable
data class SerializableDeliveryRates(
    val defaultBoxRetention: Int,
    val defaultPaletteSize: Int,
    val defaultPaletteInterval: Long
) {
    val asDeliveryRates: DeliveryRates
        get() = DeliveryRates(
            defaultBoxRetention,
            defaultPaletteSize,
            defaultPaletteInterval.milliseconds
        )

    companion object {
        fun from(deliveryRates: DeliveryRates): SerializableDeliveryRates {
            return SerializableDeliveryRates(
                defaultBoxRetention = deliveryRates.defaultBoxRetention,
                defaultPaletteSize = deliveryRates.defaultPaletteSize,
                defaultPaletteInterval = deliveryRates.defaultPaletteInterval.inWholeMilliseconds
            )
        }
    }
}

enum class TargetStatus {
    NONE,
    BUILDING,
    BUILT,
    ERROR
}

@Serializable
data class ScriptTargetInfo(
    val name: String,
    val status: TargetStatus
)
