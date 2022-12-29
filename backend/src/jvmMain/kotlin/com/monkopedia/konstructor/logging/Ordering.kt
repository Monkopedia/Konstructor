package com.monkopedia.konstructor.logging

import com.monkopedia.hauler.Box
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Flow<Box>.ordered(maxDelay: Duration = 1.seconds): Flow<Box> {
    return flow {
        val list = mutableListOf<Box>()

        merge(this@ordered, flow { while (true) emit(Unit) }.onEach { delay(maxDelay) }).collect {
            if (it is Box) {
                list.add(it)
                list.sortByDescending { b -> b.timestamp }
            }
            val cutouff = System.currentTimeMillis() - maxDelay.inWholeMilliseconds
            while (list.last().timestamp < cutouff) {
                emit(list.removeLast())
            }
        }
    }
}
