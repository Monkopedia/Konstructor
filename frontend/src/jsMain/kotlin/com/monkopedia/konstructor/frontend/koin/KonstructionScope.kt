package com.monkopedia.konstructor.frontend.koin

import com.monkopedia.konstructor.frontend.model.KonstructionModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.ScopeDSL

class KonstructionScope(private val workspaceScope: WorkspaceScope, val konstructionId: String) : CoroutineKoinScope() {

    override fun onScopeCreated(scope: Scope) {
        scope.linkTo(workspaceScope.scope)
    }
    companion object {
        val konstructionId = named("konstruction.id")

        fun ScopeDSL.initScope() {
            scoped {
                get<KonstructionScope>().coroutineScope
            }
            scoped(konstructionId) {
                get<KonstructionScope>().konstructionId
            }
            scoped {
                KonstructionModel(get(), get(), get(konstructionId), get())
            }
        }
    }
}