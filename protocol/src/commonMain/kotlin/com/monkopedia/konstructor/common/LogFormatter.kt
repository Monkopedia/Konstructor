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
            if (box.threadName != null) "$time ${box.level} (${box.threadName}) ${box.loggerName} - "
            else "$time ${box.level} ${box.loggerName} - "
        box.message.split("\n").forEach {
            emit(prefix + it)
        }
    }
}
