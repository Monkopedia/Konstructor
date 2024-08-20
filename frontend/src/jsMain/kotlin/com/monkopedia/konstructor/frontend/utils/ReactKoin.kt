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
            createdScope.scope.close()
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
