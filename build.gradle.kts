buildscript {
    val kotlin_version by extra("1.7.20")
    repositories {
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlin_version")
        classpath("com.monkopedia.ksrpc:ksrpc-gradle-plugin:0.7.1")
        classpath("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")
    }
}
plugins {
    id("com.github.autostyle") version "3.1"
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
        jcenter()
        mavenCentral()
        mavenLocal()
    }
}
