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
package com.monkopedia.konstructor.lib.logging

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.slf4j.ILoggerFactory
import org.slf4j.Logger

class HaulerLoggerFactory : ILoggerFactory {
    var loggerMap: ConcurrentMap<String, Logger> = ConcurrentHashMap()

    @OptIn(DelicateCoroutinesApi::class)
    override fun getLogger(name: String): Logger {
        val simpleLogger = loggerMap[name]
        return if (simpleLogger != null) {
            simpleLogger
        } else {
            val newInstance: Logger = HaulerLogger(name, GlobalScope)
            val oldInstance = loggerMap.putIfAbsent(name, newInstance)
            oldInstance ?: newInstance
        }
    }
}
