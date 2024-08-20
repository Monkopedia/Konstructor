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
//    id("com.monkopedia.ksrpc.plugin")
}

version = "0.1"

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    js(IR) {
        browser {
//            useCommonJs()
            webpackTask {
                output.libraryTarget =
                    org.jetbrains.kotlin.gradle.targets.js.webpack
                        .KotlinWebpackOutput.Target.COMMONJS
            }
            dceTask {
                keep += "kotlin.defineModule"
                keep += "io.ktor.http.Headers"
                keep += "kotlin.math.pow"
                println("Adding to $name")
            }
        }
        binaries.executable()
    }
    sourceSets["jsMain"].apply {
        kotlin.srcDir("three-kt/threejs-wrapper/src/main/kotlin")
        kotlin.exclude {
            it.name == "Math.kt"
        }
    }
    sourceSets["jsMain"].dependencies {
        implementation(libs.ksrpc.ktor.client)
        implementation(libs.ksrpc.ktor.websocket.client)
        implementation(libs.koin.core)
        implementation(kotlin("stdlib-js"))
        compileOnly(libs.ktor.client.core)
        implementation(libs.kotlin.emotion)
        implementation(libs.kotlin.css)
        implementation(libs.kotlin.styled.next)
        implementation(libs.kotlin.react)
        implementation(libs.kotlin.react.dom)
        implementation(libs.kotlin.react.router.dom)
        implementation(libs.kotlin.extensions)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.ktor.http)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.websockets)
        implementation(libs.ktor.io)
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlin.mui)
        implementation(libs.kotlin.mui.icons)
        implementation(project(":protocol"))
        implementation(npm("codemirror", "6.0.1"))
        implementation(npm("@codemirror/theme-one-dark", "6.1.2"))
        implementation(npm("@codemirror/legacy-modes", "6.3.3"))
        implementation(npm("@codemirror/language", "6.10.1"))
        implementation(npm("@codemirror/commands", "6.3.3"))
        implementation(npm("@codemirror/view", "6.26.1"))
        implementation(npm("@replit/codemirror-vim", "6.2.1"))
        implementation(npm("css-loader", "6.10.0"))
        implementation(npm("style-loader", "3.3.4"))
        implementation(npm("file-loader", "6.2.0"))
        implementation(npm("bootstrap", "^5.3.3"))
        implementation(npm("crypto", "1.0.1"))
        implementation(npm("crypto-browserify", "3.12.0"))
//        implementation(npm("three", "0.133.1"))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        freeCompilerArgs += "-Xskip-prerelease-check"
        freeCompilerArgs += "-Xno-param-assertions"
    }
}
