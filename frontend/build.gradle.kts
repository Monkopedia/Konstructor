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
    id("com.monkopedia.ksrpc.plugin")
}

version = "0.1"

repositories {
    jcenter()
    mavenCentral()
    mavenLocal()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-dev/")
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap/")
    maven(url = "https://kotlinx.bintray.com/kotlinx/")
    maven("https://kotlin.bintray.com/kotlin-js-wrappers")
    maven(url = "https://jitpack.io")
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
        implementation("com.monkopedia.ksrpc:ksrpc-ktor-client:0.7.2")
        implementation("com.monkopedia.ksrpc:ksrpc-ktor-websocket-client:0.7.2")
        implementation("io.insert-koin:koin-core:3.3.2")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-js:1.8.0")
        compileOnly("io.ktor:ktor-client-core:2.2.1")
        compileOnly("io.ktor:ktor-client-js:2.2.1")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion:11.10.5-pre.464")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-css:1.0.0-pre.464")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-styled-next:1.2.1-pre.464")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-react:18.2.0-pre.464")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:18.2.0-pre.464")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:6.3.0-pre.464")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-extensions:1.0.1-pre.464")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.4")
        implementation("io.ktor:ktor-http:2.2.1")
        implementation("io.ktor:ktor-client-core:2.2.1")
        implementation("io.ktor:ktor-client-websockets:2.2.1")
        implementation("io.ktor:ktor-io:2.2.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-mui:5.9.1-pre.464")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-mui-icons:5.10.9-pre.464")
        implementation(project(":protocol"))
        implementation(npm("codemirror", "6.0.1"))
        implementation(npm("@codemirror/theme-one-dark", "6.1.0"))
        implementation(npm("@codemirror/legacy-modes", "6.1.0"))
        implementation(npm("@codemirror/language", "6.2.1"))
        implementation(npm("@codemirror/commands", "6.1.1"))
        implementation(npm("@replit/codemirror-vim", "6.0.3"))
        implementation(npm("material-ui-color-picker", "3.5.1"))
        implementation(npm("@material-ui/core", "4.12.4"))
        implementation(npm("css-loader", "3.5.2"))
        implementation(npm("style-loader", "1.1.3"))
        implementation(npm("bootstrap", "^4.4.1"))
        implementation(npm("crypto", "1.0.1"))
        implementation(npm("crypto-browserify", "3.12.0"))
        implementation(npm("three", "0.133.1"))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-prerelease-check"
        freeCompilerArgs += "-Xno-param-assertions"
    }
}
