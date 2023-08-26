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
import com.monkopedia.hauler.DeliveryService
import com.monkopedia.hauler.DropBox
import com.monkopedia.hauler.LoadingDock
import com.monkopedia.hauler.LoggerMatchMode.PREFIX
import com.monkopedia.hauler.Palette
import com.monkopedia.hauler.Shipper
import com.monkopedia.hauler.ThreadNameFilter

class ScopedShipper(
    private val tagPrefix: String,
    name: String,
    private val shipper: Shipper
) : Shipper {
    private val fullPrefix = "$tagPrefix.$name"
    override suspend fun deliveries(u: Unit): DeliveryService {
        return shipper.deliveries().weighIn(ThreadNameFilter(PREFIX, tagPrefix))
    }

    override suspend fun requestDockPickup(u: Unit): LoadingDock {
        val loadingDock = shipper.requestDockPickup()
        return object : LoadingDock {
            override suspend fun bulkLog(logs: Palette) {
                val defaultIndex = logs.threadNames.size
                loadingDock.bulkLog(
                    logs.copy(
                        threadNames = logs.threadNames.map { "$fullPrefix.$it" } + fullPrefix,
                        messages = logs.messages.map {
                            if (it.threadName == null) it.copy(threadName = defaultIndex)
                            else it
                        }
                    )
                )
            }

            override suspend fun close() {
                loadingDock.close()
            }
        }
    }

    override suspend fun requestPickup(u: Unit): DropBox {
        val pickup = requestPickup()
        return object : DropBox {
            override suspend fun log(logEvent: Box) {
                pickup.log(
                    logEvent.copy(
                        threadName = "$fullPrefix${logEvent.threadName?.let { ".$it" } ?: ""}"
                    )
                )
            }

            override suspend fun close() {
                pickup.close()
            }
        }
    }
}
