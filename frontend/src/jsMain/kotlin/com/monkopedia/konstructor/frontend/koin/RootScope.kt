package com.monkopedia.konstructor.frontend.koin

import com.monkopedia.konstructor.frontend.model.ServiceHolder
import com.monkopedia.konstructor.frontend.model.SpaceListModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.dsl.module

object RootScope : KoinComponent {
    @OptIn(DelicateCoroutinesApi::class)
    val rootModule = module {
        single { ServiceHolder(get()) }
        single {
            SpaceListModel(get(), get())
        }
        single {
            GlobalScope as CoroutineScope
        }
        single {
            ScopeTracker(get(), get())
        }
        factory { (workspaceId: String) ->
            WorkspaceScope(workspaceId)
        }

        scope<WorkspaceScope> {
            with(WorkspaceScope.Companion) {
                initScope()
            }
            factory { (konstructionId: String) ->
                KonstructionScope(get(), konstructionId)
            }
        }
        scope<KonstructionScope> {
            with(KonstructionScope.Companion) {
                initScope()
            }
        }
    }

    val scopeTracker by inject<ScopeTracker>()
    val serviceHolder by inject<ServiceHolder>()
    val spaceListModel by inject<SpaceListModel>()

    fun init() {
        startKoin {
            loadKoinModules(rootModule)
        }
    }
}
