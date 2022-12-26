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

import com.monkopedia.konstructor.frontend.WorkManager
import com.monkopedia.konstructor.frontend.model.GlControlsModel
import com.monkopedia.konstructor.frontend.model.GlobalDialogsModel
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
        single {
            GlControlsModel(get())
        }
        factory { (workspaceId: String) ->
            WorkspaceScope(workspaceId)
        }
        factory { (workManager: WorkManager) ->
            NavigationDialogModel(get(), workManager)
        }
        factory { (workManager: WorkManager) ->
            GlobalDialogsModel(get(), workManager, get())
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
