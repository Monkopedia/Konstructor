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
package com.monkopedia.konstructor.lib

import java.io.Closeable
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking

internal class BlockableThread(label: String) : Closeable {
    private val threadLocal = ThreadLocal.withInitial { false }

    @OptIn(DelicateCoroutinesApi::class)
    val dispatcher = newSingleThreadContext(label).also {
        it.executor.execute {
            threadLocal.set(true)
        }
    }

    /**
     * Acts like a [kotlinx.coroutines.runBlocking] except will only activate if on this thread
     * and throws an exception if
     */
    fun <T> blockForSuspension(exec: suspend () -> T): T {
        if (!threadLocal.get()) {
            throw IllegalStateException("Can only block when on thread of this BlockableThread")
        }
        return runBlocking(ContextElement(this) + Dispatchers.IO) {
            exec()
        }
    }

    override fun close() = this.dispatcher.close()

    private class ContextElement(val thread: BlockableThread) : ThreadContextElement<Unit> {
        override val key: Key
            get() = Key

        object Key : CoroutineContext.Key<ContextElement>

        override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) = Unit

        override fun updateThreadContext(context: CoroutineContext) {
            if (context[ContinuationInterceptor.Key] === thread.dispatcher ||
                thread.threadLocal.get()
            ) {
                throw IllegalStateException(
                    "Cannot return to source dispatcher while blocking $thread"
                )
            }
        }
    }
}
