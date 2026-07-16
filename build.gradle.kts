buildscript {
    repositories {
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlin.gradle)
        classpath(libs.kotlin.serialization)
        classpath(libs.ksrpc)
    }
}
plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.shadow) apply false
}

val apacheHeader = """
    |/*
    | * Copyright 2022 Jason Monk
    | *
    | * Licensed under the Apache License, Version 2.0 (the "License");
    | * you may not use this file except in compliance with the License.
    | * You may obtain a copy of the License at
    | *
    | *     https://www.apache.org/licenses/LICENSE-2.0
    | *
    | * Unless required by applicable law or agreed to in writing, software
    | * distributed under the License is distributed on an "AS IS" BASIS,
    | * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    | * See the License for the specific language governing permissions and
    | * limitations under the License.
    | */
""".trimMargin()

subprojects {
    plugins.apply("com.diffplug.spotless")
    spotless {
        kotlin {
            target("**/*.kt")
            targetExclude("**/three-kt/**", "**/build/**", "**/generated/**")
            ktlint(libs.versions.ktlint.get())
                .editorConfigOverride(
                    mapOf(
                        // Compose conventions: PascalCase @Composable functions,
                        // uppercase CompositionLocal/StateFlow val names.
                        "ktlint_function_naming_ignore_when_annotated_with" to "Composable"
                    )
                )
            licenseHeader(apacheHeader)
        }
        kotlinGradle {
            target("**/*.gradle.kts")
            targetExclude("**/three-kt/**", "**/build/**")
            ktlint(libs.versions.ktlint.get())
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

// Pin the Node.js version used by the Kotlin/JS + Wasm toolchains. Compose-wasm's
// default Node download otherwise resolves to the latest release (25.0.0), which
// breaks wasmJsBrowserProductionWebpack on a fresh CI runner.
//
// The download version is driven per-compilation by each project's own node
// EnvSpec (kotlinNodeJsSpec / kotlinWasmNodeJsSpec) — NOT by the root-project
// spec or by the deprecated WasmNodeJsRootExtension.version. Setting it only on
// the root project (as before) left :frontend's and :protocol's EnvSpecs at the
// default 25.0.0, so the frontend webpack still downloaded 25.0.0. We therefore
// apply the pin to every project's EnvSpec. Only the non-deprecated
// EnvSpec.version Property API is used (the WasmNodeJsRootExtension.version =
// setter fails under -Werror and is scheduled for removal in Kotlin 2.3).
val pinnedNodeVersion = "22.11.0"
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version.set(pinnedNodeVersion)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().version.set(pinnedNodeVersion)
    }
}
