package com.monkopedia.konstructor.frontend.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import react.useState

inline fun useEffect(
    vararg deps: Any,
    crossinline action: suspend CoroutineScope.() -> Unit
) {
    react.useEffect(*deps) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job)
        scope.launch {
            action()
        }
        cleanup {
            job.cancel()
        }
    }
}

inline fun <reified T : Any?> Flow<T>.useCollected(initial: T): T {
    val (state, setState) = useState(initial)
    useEffect(this@useCollected) {
        collect { value ->
            setState(value)
        }
    }
    return state
}

inline fun <reified T : Any?> Flow<T>.useCollected(): T? =
    useCollected(null)

inline fun <reified T : Any?> Flow<T>.useDistinctState(initial: T):  T =
    distinctUntilChanged().useCollected(initial)

inline fun <reified T : Any> Flow<T>.useDistinctState(): T? =
    useDistinctState(null)
