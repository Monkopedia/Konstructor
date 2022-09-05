package com.monkopedia.konstructor.frontend.koin

import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.model.NavigationDialogModel
import com.monkopedia.konstructor.frontend.model.ServiceHolder
import com.monkopedia.konstructor.frontend.model.SettingsModel
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
        single {
            SettingsModel(get())
        }
        factory { (workspaceId: String) ->
            WorkspaceScope(workspaceId)
        }
        factory { (workManager: WorkManager) ->
            NavigationDialogModel(get(), workManager)
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
    val settingsModel by inject<SettingsModel>()
    val spaceListModel by inject<SpaceListModel>()
    val serviceHolder by inject<ServiceHolder>()

    fun init() {
        startKoin {
            loadKoinModules(rootModule)
        }
    }
}
