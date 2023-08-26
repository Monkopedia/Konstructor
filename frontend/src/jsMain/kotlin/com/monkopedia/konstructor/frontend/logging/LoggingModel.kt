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
package com.monkopedia.konstructor.frontend.logging

import com.monkopedia.hauler.deliveries
import com.monkopedia.hauler.withDeliveryDay
import com.monkopedia.konstructor.common.LogFormatter
import com.monkopedia.konstructor.frontend.model.ServiceHolder
import com.monkopedia.konstructor.frontend.model.ServiceHolder.Companion.tryReconnects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

class LoggingModel(scope: CoroutineScope, serviceHolder: ServiceHolder) {
    private val mutableLoggingOpen = MutableStateFlow(false)
    val isLoggingOpen: Flow<Boolean> = mutableLoggingOpen

    @OptIn(ExperimentalCoroutinesApi::class)
    val logLines = callbackFlow<Array<String>> {
        val lines = mutableListOf<String>()
        val collection = launch {
            serviceHolder.service.flatMapLatest { service ->
                service.getGlobalShipper().deliveries().withDeliveryDay()
            }.transform(LogFormatter::invoke).collect { message ->
                lines += message
                send(lines.toTypedArray())
            }
        }
        awaitClose {
            collection.cancel()
        }
    }.tryReconnects().shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 1)

    fun openLogging() {
        mutableLoggingOpen.value = true
    }

    fun closeLogging() {
        mutableLoggingOpen.value = false
    }
}
