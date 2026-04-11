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
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

version = "0.1"

repositories {
    google()
    mavenCentral()
}

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }
    sourceSets["commonMain"].dependencies {
        implementation(compose.components.resources)
    }
    sourceSets["wasmJsMain"].dependencies {
        // Compose
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation("org.jetbrains.compose.ui:ui:${libs.versions.compose.multiplatform.get()}")
        implementation(compose.materialIconsExtended)

        // Lifecycle + ViewModel
        implementation(libs.lifecycle.viewmodel.compose)

        // Koin
        implementation(libs.koin.core)
        implementation(libs.koin.compose)
        implementation(libs.koin.compose.viewmodel)

        // kodemirror
        implementation(project.dependencies.platform("com.monkopedia.kodemirror:kodemirror-bom:${libs.versions.kodemirror.get()}"))
        implementation("com.monkopedia.kodemirror:view")
        implementation("com.monkopedia.kodemirror:state")
        implementation("com.monkopedia.kodemirror:basic-setup")
        implementation("com.monkopedia.kodemirror:theme-one-dark")
        implementation("com.monkopedia.kodemirror:theme-dracula")
        implementation("com.monkopedia.kodemirror:theme-amy")
        implementation("com.monkopedia.kodemirror:theme-ayu-light")
        implementation("com.monkopedia.kodemirror:theme-barf")
        implementation("com.monkopedia.kodemirror:theme-bespin")
        implementation("com.monkopedia.kodemirror:theme-birds-of-paradise")
        implementation("com.monkopedia.kodemirror:theme-boys-and-girls")
        implementation("com.monkopedia.kodemirror:theme-clouds")
        implementation("com.monkopedia.kodemirror:theme-cobalt")
        implementation("com.monkopedia.kodemirror:theme-cool-glow")
        implementation("com.monkopedia.kodemirror:theme-espresso")
        implementation("com.monkopedia.kodemirror:theme-noctis-lilac")
        implementation("com.monkopedia.kodemirror:theme-rose-pine-dawn")
        implementation("com.monkopedia.kodemirror:theme-smoothy")
        implementation("com.monkopedia.kodemirror:theme-solarized-light")
        implementation("com.monkopedia.kodemirror:theme-tomorrow")
        implementation("com.monkopedia.kodemirror:material-theme")
        implementation("com.monkopedia.kodemirror:legacy-modes")
        implementation("com.monkopedia.kodemirror:vim:${libs.versions.kodemirror.get()}")
        implementation("com.monkopedia.kodemirror:commands")
        implementation("com.monkopedia.kodemirror:language")
        implementation("com.monkopedia.kodemirror:lezer-highlight")

        // Network / RPC
        implementation(libs.ksrpc.ktor.client)
        implementation(libs.ksrpc.ktor.websocket.client)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.websockets)
        implementation(libs.ktor.http)
        implementation(libs.ktor.io)

        // Kotlin libraries
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)

        // Three.js 3D rendering
        implementation(npm("three", "^0.170.0"))

        // Protocol
        implementation(project(":protocol"))
    }
}

kotlin.compilerOptions {
    freeCompilerArgs.addAll("-Xskip-prerelease-check")
}
