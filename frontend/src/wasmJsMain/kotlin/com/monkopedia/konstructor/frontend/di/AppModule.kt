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
package com.monkopedia.konstructor.frontend.di

import com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel
import com.monkopedia.konstructor.frontend.viewmodel.NavigationDialogViewModel
import com.monkopedia.konstructor.frontend.viewmodel.ServiceHolder
import com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel
import com.monkopedia.konstructor.frontend.viewmodel.SpaceListViewModel
import com.monkopedia.konstructor.frontend.viewmodel.TargetDisplayRepository
import com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel
import org.koin.dsl.module

val appModule = module {
    single { ServiceHolder() }
    single { SettingsViewModel() }
    single { TargetDisplayRepository() }

    single { SpaceListViewModel(get()) }
    single { WorkspaceViewModel(get()) }
    single { KonstructionViewModel(get(), get()) }
    single { NavigationDialogViewModel(get()) }
}
