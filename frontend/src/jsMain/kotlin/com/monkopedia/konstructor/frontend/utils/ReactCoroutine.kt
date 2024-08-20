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
package com.monkopedia.konstructor.frontend.utils

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import react.useEffect
import react.useState

suspend inline fun cleanup(function: () -> Unit) {
    try {
        awaitCancellation()
    } finally {
        function()
    }
}

//inline fun useEffect(
//    vararg deps: Any,
//    crossinline action: suspend CoroutineScope.() -> Unit
//) {
//    react.useEffect(*deps) {
//        val job = SupervisorJob()
//        val scope = CoroutineScope(job)
//        scope.launch {
//            action()
//        }
//        cleanup {
//            job.cancel()
//        }
//    }
//}

inline fun <reified T : Any?> Flow<T>.useCollected(initial: T): T {
    val (state, setState) = useState(initial)
    useEffect(this@useCollected, setState) {
        collect { value ->
            setState(value)
        }
    }
    return state
}

inline fun <reified T : Any?> Flow<T>.useCollected(): T? =
    useCollected(null)

inline fun <reified T : Any?> Flow<T>.useDistinctState(initial: T): T =
    distinctUntilChanged().useCollected(initial)

inline fun <reified T : Any> Flow<T>.useDistinctState(): T? =
    useDistinctState(null)
