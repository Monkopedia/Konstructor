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
package com.monkopedia.konstructor.frontend.koin

import com.monkopedia.hauler.error
import com.monkopedia.hauler.hauler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.createScope
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeCallback

open class CoroutineKoinScope(parent: Job? = null) : KoinScopeComponent {
    private val job = SupervisorJob(parent)
    private val logScope = CoroutineScope(job)
    val coroutineScope = CoroutineScope(
        job + CoroutineExceptionHandler { coroutineContext, throwable ->
            logScope.launch {
                hauler("CoroutineKoinScope").error("Exception caught in $coroutineContext", throwable)
            }
        },
    )

    override val scope: Scope by lazy {
        createScope(this).also {
            it.registerCallback(object : ScopeCallback {
                override fun onScopeClose(scope: Scope) {
                    job.cancel()
                }
            })
            onScopeCreated(it)
        }
    }

    protected open fun onScopeCreated(scope: Scope) = Unit
}
