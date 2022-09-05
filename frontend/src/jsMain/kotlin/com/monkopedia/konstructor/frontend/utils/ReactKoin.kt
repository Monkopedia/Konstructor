package com.monkopedia.konstructor.frontend.utils

import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.get
import react.useMemo
import react.useRef
import react.useState

inline fun <reified T : KoinScopeComponent> KoinComponent.useSubScope(
    crossinline getter: KoinComponent.() -> T = { get() }
): T {
    val createdScope = useMemo(this) {
        getter()
    }
    val first = useRef(true)
    val (scope, setScope) = useState(createdScope)
    react.useEffect(this@useSubScope) {
        if (first.current == true) {
            first.current = false
        } else {
            setScope(createdScope)
        }
        cleanup {
            createdScope.closeScope()
        }
    }
    return scope
}

inline fun <reified T : Closeable> KoinComponent.useCloseable(
    crossinline getter: KoinComponent.() -> T = { get() }
): T {
    val createdScope = useMemo(this) {
        getter()
    }
    val first = useRef(true)
    val (scope, setScope) = useState(createdScope)
    react.useEffect(this@useCloseable) {
        if (first.current == true) {
            first.current = false
        } else {
            setScope(createdScope)
        }
        cleanup {
            createdScope.close()
        }
    }
    return scope
}


inline fun <reified T> KoinComponent.useCollected(
    crossinline flow: KoinComponent.() -> Flow<T>,
): T? = useCollected(null, flow)

inline fun <reified T> KoinComponent.useCollected(
    initial: T,
    crossinline flow: KoinComponent.() -> Flow<T>
): T {
    val flow = useMemo(this@useCollected) {
        flow()
    }
    return flow.useCollected(initial)
}
