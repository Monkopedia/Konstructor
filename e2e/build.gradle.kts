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
    useJUnit()
}
