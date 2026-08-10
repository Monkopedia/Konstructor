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
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation("com.microsoft.playwright:playwright:1.58.0")
    testImplementation(project(":protocol"))
    testImplementation(libs.ksrpc.core)
    testImplementation(libs.ksrpc.ktor.client)
    testImplementation(libs.ksrpc.ktor.websocket.client)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    onlyIf { project.hasProperty("e2e") }
    dependsOn(":backend:shadowJar")
    val jarFile = project(":backend")
        .layout.buildDirectory.file("libs/backend-all.jar")
    systemProperty("konstructor.jar", jarFile.get().asFile.absolutePath)
    // Forward opt-in flags to the forked test JVM. Gradle does not propagate -D
    // system properties or -P project properties to test workers automatically,
    // so without this the `capture`/`updateBaselines` gates always read null.
    //  - `capture`: run the assertion-free screenshot/manual capture tools.
    //  - `updateBaselines`: on a missing baseline, write the captured actual as
    //    the new baseline and skip (instead of failing).
    // A bare `-Pcapture` / `-PupdateBaselines` (no value) is treated as opt-in.
    listOf("capture", "updateBaselines").forEach { key ->
        if (project.hasProperty(key) || System.getProperty(key) != null) {
            val raw = (project.findProperty(key) as? String) ?: System.getProperty(key)
            systemProperty(key, if (raw.isNullOrEmpty()) "true" else raw)
        }
    }
    useJUnit()
}
