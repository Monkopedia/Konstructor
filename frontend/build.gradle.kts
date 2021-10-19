/*
 * Copyright 2020 Jason Monk
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
    kotlin("plugin.serialization") version "1.5.31"
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
                    org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackOutput.Target.COMMONJS
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
        implementation("org.jetbrains.kotlin:kotlin-stdlib-js:1.5.31")
        compileOnly("io.ktor:ktor-client-core:1.6.4")
        compileOnly("io.ktor:ktor-client-js:1.6.4")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-css:1.0.0-pre.236-kotlin-1.5.30")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-styled:5.3.0-pre.236-kotlin-1.5.30")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-react:17.0.2-pre.236-kotlin-1.5.30")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:17.0.2-pre.236-kotlin-1.5.30")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:5.2.0-pre.236-kotlin-1.5.30")
        implementation("org.jetbrains.kotlin-wrappers:kotlin-extensions:1.0.1-pre.236-kotlin-1.5.30")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.5.2-native-mt")
        implementation("io.ktor:ktor-http:1.5.0")
        implementation("io.ktor:ktor-http-cio:1.5.0")
        implementation("io.ktor:ktor-client-core:1.5.0")
        implementation("io.ktor:ktor-io:1.5.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.0.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
        implementation("com.ccfraser.muirwik:muirwik-components:0.9.0")
        implementation(project(":common"))
        implementation(npm("codemirror", "5.63.3"))
        implementation(npm("css-loader", "3.5.2"))
        implementation(npm("style-loader", "1.1.3"))
        implementation(npm("bootstrap", "^4.4.1"))
        implementation(npm("crypto", "1.0.1"))
        implementation(npm("crypto-browserify", "3.12.0"))
        implementation(npm("react", "17.0.2"))
        implementation(npm("react-dom", "17.0.2"))
        implementation(npm("react-router-dom", "5.3.0"))
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
