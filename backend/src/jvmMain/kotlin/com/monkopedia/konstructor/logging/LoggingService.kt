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
