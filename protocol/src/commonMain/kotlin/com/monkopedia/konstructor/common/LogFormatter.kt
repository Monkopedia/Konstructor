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
package com.monkopedia.konstructor.common

import com.monkopedia.hauler.Box
import com.monkopedia.hauler.Formatter
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object LogFormatter : Formatter {
    override suspend fun invoke(collector: FlowCollector<String>, box: Box) {
        collector.format(box)
    }

    private suspend fun FlowCollector<String>.format(box: Box) {
        val time = Instant.fromEpochMilliseconds(box.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val prefix =
            if (box.threadName != null) {
                "$time ${box.level} (${box.threadName}) ${box.loggerName} - "
            } else "$time ${box.level} ${box.loggerName} - "
        box.message.split("\n").forEach {
            emit(prefix + it)
        }
    }
}
