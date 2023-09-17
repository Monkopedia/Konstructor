buildscript {
    val kotlin_version by extra("1.8.0")
    repositories {
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlin.gradle)
        classpath(libs.kotlin.serialization)
        classpath(libs.ksrpc)
        classpath(libs.shadow)
    }
}
plugins {
    alias(libs.plugins.autostyle)
}

subprojects {
    plugins.apply("com.github.autostyle")
    autostyle {
        kotlinGradle {
            // Since kotlin doesn't pick up on multi platform projects
            filter.include("**/*.kt")
            filter.exclude("**/three-kt/**")
            ktlint("0.42.1") {
                userData(mapOf("android" to "true"))
            }

            licenseHeader(
                """
                |Copyright 2022 Jason Monk
                |
                |Licensed under the Apache License, Version 2.0 (the "License");
                |you may not use this file except in compliance with the License.
                |You may obtain a copy of the License at
                |
                |    https://www.apache.org/licenses/LICENSE-2.0
                |
                |Unless required by applicable law or agreed to in writing, software
                |distributed under the License is distributed on an "AS IS" BASIS,
                |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                |See the License for the specific language governing permissions and
                |limitations under the License.""".trimMargin()
            )
        }
    }
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}
