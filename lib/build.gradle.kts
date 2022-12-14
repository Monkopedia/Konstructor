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
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.monkopedia.ksrpc.plugin")
    id("com.github.johnrengelman.shadow")
}

repositories {
    mavenCentral()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-prerelease-check"
        freeCompilerArgs += "-Xno-param-assertions"
    }
}

dependencies {
    implementation("com.monkopedia.ksrpc:ksrpc-core:0.7.1")
    implementation("com.monkopedia.ksrpc:ksrpc-sockets:0.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
    implementation("io.ktor:ktor-io:2.0.2")
    implementation("io.ktor:ktor-client-cio:2.0.2")
    api("com.monkopedia:kcsg-lib")
    api("com.monkopedia:kcsg")
}
