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

import com.monkopedia.hauler.Box
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

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
