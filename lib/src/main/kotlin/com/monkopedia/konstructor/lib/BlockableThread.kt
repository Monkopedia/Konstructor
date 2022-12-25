package com.monkopedia.konstructor.lib

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

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
            if (context[ContinuationInterceptor.Key] === thread.dispatcher || thread.threadLocal.get()) {
                throw IllegalStateException("Cannot return to source dispatcher while blocking $thread")
            }
        }
    }
}
