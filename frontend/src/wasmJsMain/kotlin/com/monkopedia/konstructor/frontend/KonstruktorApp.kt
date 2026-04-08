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
package com.monkopedia.konstructor.frontend

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.monkopedia.konstructor.frontend.di.appModule
import com.monkopedia.konstructor.frontend.ui.Initializer
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@Composable
fun KonstruktorApp() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        // Install the test bridge for Playwright e2e testing
        InstallTestBridge()

        MaterialTheme(colorScheme = KonstruktorColorScheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Initializer()
            }
        }
    }
}

@Composable
private fun InstallTestBridge() {
    val scope = rememberCoroutineScope()
    // Inject dependencies to initialize TestBridge
    val serviceHolder = koinInject<com.monkopedia.konstructor.frontend.viewmodel.ServiceHolder>()
    val spaceListVm = koinInject<com.monkopedia.konstructor.frontend.viewmodel.SpaceListViewModel>()
    val settingsVm = koinInject<com.monkopedia.konstructor.frontend.viewmodel.SettingsViewModel>()
    val konstructionVm = koinInject<com.monkopedia.konstructor.frontend.viewmodel.KonstructionViewModel>()
    val workspaceVm = koinInject<com.monkopedia.konstructor.frontend.viewmodel.WorkspaceViewModel>()

    LaunchedEffect(Unit) {
        TestBridge.install(scope, serviceHolder, spaceListVm, settingsVm, konstructionVm, workspaceVm)
    }
}
