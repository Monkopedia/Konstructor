package com.monkopedia.konstructor.frontend.koin

import com.monkopedia.konstructor.frontend.model.WorkspaceModel
import org.koin.core.qualifier.named
import org.koin.dsl.ScopeDSL

class WorkspaceScope(val workspaceId: String) : CoroutineKoinScope() {
    companion object {
        val workspaceId = named("workspace.id")

        fun ScopeDSL.initScope() {
            scoped {
                get<WorkspaceScope>().coroutineScope
            }
            scoped(workspaceId) {
                get<WorkspaceScope>().workspaceId
            }
            scoped {
                WorkspaceModel(get(), get(workspaceId), get())
            }
        }
    }
}