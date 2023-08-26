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
    implementation(libs.ksrpc.core)
    implementation(libs.ksrpc.sockets)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.io)
    implementation(libs.ktor.client.cio)
    implementation(libs.slf4j.api)
    api(libs.kcsg.dsl)
    api(libs.kcsg)
    api(libs.hauler)
}
