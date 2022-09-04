package com.monkopedia.konstructor.frontend.koin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.createScope
import org.koin.core.scope.Scope

open class CoroutineKoinScope(parent: Job? = null) : KoinScopeComponent {
    private val job = SupervisorJob(parent)
    val coroutineScope = CoroutineScope(job)

    override val scope: Scope by lazy {
        createScope(this).also {
            onScopeCreated(it)
        }
    }

    protected open fun onScopeCreated(scope: Scope) = Unit

    override fun closeScope() {
        job.cancel()
        super.closeScope()
    }
}
