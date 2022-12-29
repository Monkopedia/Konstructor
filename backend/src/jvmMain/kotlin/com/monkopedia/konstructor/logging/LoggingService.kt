package com.monkopedia.konstructor.logging

import com.monkopedia.hauler.CallSign
import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import com.monkopedia.konstructor.logging.LoggingService.Companion.INCLUDE_TRACE_HASH
import kotlinx.coroutines.withContext

interface LoggingService {
    val serviceName: String

    companion object {
        const val INCLUDE_TRACE_HASH = false
    }
}

suspend inline fun <T> LoggingService.callContext(
    callName: String? = null,
    baseCallSign: CallSign? = null,
    crossinline exec: suspend () -> T
): T {
    val name = (
        if (INCLUDE_TRACE_HASH) {
            listOfNotNull(serviceName, callName, Any().hashCode())
        } else listOfNotNull(serviceName, callName)
        ).joinToString(".")
    return withContext(baseCallSign?.times(CallSign(name)) ?: CallSign(name)) {
        try {
            exec()
        } catch (t: Throwable) {
            hauler().error("Throwing from callContext", t)
            throw t
        }
    }
}

operator fun CallSign.times(other: CallSign): CallSign {
    return CallSign("$name.${other.name}")
}
