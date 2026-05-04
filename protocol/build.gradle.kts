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

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    wasmJs {
        browser {}
    }
    jvm {
    }
    sourceSets["commonMain"].dependencies {
        api(libs.ksrpc.core)
        api(libs.ksrpc.binary.ktor)
        api(libs.hauler)
        api(libs.kotlinx.datetime)
        // ByteReadChannel is in the public KonstructionService surface —
        // must be api so consumers don't have to redeclare ktor-io.
        api(libs.ktor.io)
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines.core)
    }
    sourceSets["jvmMain"].dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        implementation(libs.slf4j.api)

        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.logback.classic)
    }
    sourceSets["commonTest"].dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.kotlinx.serialization.json)
    }
    sourceSets["jvmTest"].dependencies {
        implementation(kotlin("test-junit"))
    }
}

kotlin.compilerOptions {
    freeCompilerArgs.addAll("-Xskip-prerelease-check", "-Xno-param-assertions")
}
