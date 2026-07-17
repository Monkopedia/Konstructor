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

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

class HaulerServiceProvider : SLF4JServiceProvider {
    private lateinit var loggerFactory: ILoggerFactory
    private lateinit var markerFactory: IMarkerFactory

    // SLF4J 2.0.x binds the MDC adapter eagerly: LoggerFactory.earlyBindMDCAdapter()
    // calls getMDCAdapter() BEFORE initialize() runs. Initialize the adapter eagerly
    // (not lateinit) so that early call always returns a valid adapter instead of
    // throwing UninitializedPropertyAccessException. Behavior post-initialize is
    // unchanged: the same NOPMDCAdapter instance is used throughout.
    private val mdcAdapter: MDCAdapter = NOPMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactory
    override fun getMarkerFactory(): IMarkerFactory = markerFactory
    override fun getMDCAdapter(): MDCAdapter = mdcAdapter
    override fun getRequestedApiVersion(): String = "2.0.99"

    override fun initialize() {
        loggerFactory = HaulerLoggerFactory()
        markerFactory = BasicMarkerFactory()
    }
}
