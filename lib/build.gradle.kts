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
    id("com.gradleup.shadow")
    alias(libs.plugins.javafx)
}

repositories {
    mavenCentral()
}

kotlin.compilerOptions {
    freeCompilerArgs.addAll("-Xskip-prerelease-check", "-Xno-param-assertions")
}

// The backend bundles this shadow JAR as the `lib-all.raj` resource and locates it
// by the fixed name `lib-all.jar` (see backend copyLibToKtor). Keep the archive name
// version-independent so the project `version` in gradle.properties doesn't rename it
// to `lib-<version>-all.jar` and break the resource copy / runtime script compile.
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveVersion.set("")
}

dependencies {
    implementation(libs.ksrpc.core)
    implementation(libs.ksrpc.sockets)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.io)
    implementation(libs.ktor.client.cio)
    implementation(libs.slf4j.api)
    implementation(libs.kcsg.dsl)
    implementation(libs.kcsg)
    implementation(libs.hauler)
    api(libs.kcsg.dsl)
    api(libs.kcsg)
    api(libs.hauler)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
