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

import com.monkopedia.hauler.Box
import com.monkopedia.hauler.Level as HaulerLevel
import com.monkopedia.hauler.asAsync
import com.monkopedia.hauler.hauler
import kotlinx.coroutines.CoroutineScope
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.event.Level.DEBUG
import org.slf4j.event.Level.ERROR
import org.slf4j.event.Level.INFO
import org.slf4j.event.Level.TRACE
import org.slf4j.event.Level.WARN
import org.slf4j.helpers.LegacyAbstractLogger

class HaulerLogger internal constructor(name: String?, scope: CoroutineScope) :
    LegacyAbstractLogger() {
    private val hauler = hauler(name ?: "Logger").asAsync(scope)

    init {
        this.name = name
    }

    override fun isTraceEnabled(): Boolean = false
    override fun isDebugEnabled(): Boolean = false
    override fun isInfoEnabled(): Boolean = true
    override fun isWarnEnabled(): Boolean = true
    override fun isErrorEnabled(): Boolean = true

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker,
        messagePattern: String,
        arguments: Array<Any>?,
        throwable: Throwable?
    ) {
        hauler.emit(
            Box(
                when (level) {
                    ERROR -> HaulerLevel.ERROR
                    WARN -> HaulerLevel.WARN
                    INFO -> HaulerLevel.INFO
                    DEBUG -> HaulerLevel.DEBUG
                    TRACE -> HaulerLevel.TRACE
                },
                "",
                (
                    arguments?.let { messagePattern.format(*arguments) }
                        ?: messagePattern
                    ) +
                    (
                        throwable?.let { t ->
                            "\n${t.stackTraceToString()}"
                        } ?: ""
                        ),
                System.currentTimeMillis(),
                Thread.currentThread().name
            )

        )
    }
}
