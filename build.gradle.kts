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
