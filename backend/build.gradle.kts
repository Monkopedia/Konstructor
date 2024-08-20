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
    id("org.jetbrains.kotlin.multiplatform")
    application
    id("com.monkopedia.ksrpc.plugin")
    id("com.github.johnrengelman.shadow")
}

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets["jvmMain"].dependencies {
        implementation(libs.ksrpc.server)
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.coroutines.core.jvm)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.serialization.cbor)
        implementation(project(":protocol"))
        implementation(project(":lib"))
        implementation(libs.clikt)
        implementation(libs.ktor.server.core)
        implementation(libs.ktor.server.cors)
        implementation(libs.ktor.server.websockets)
        implementation(libs.ktor.server.status.pages)
        implementation(libs.ktor.server.netty)
        implementation(libs.ktor.websocket.serialization)
    }
    sourceSets["jvmTest"].dependencies {
        implementation(kotlin("test-junit"))
    }
}

dependencies {
    implementation(project(":lib"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xskip-prerelease-check"
    }
}

application {
    mainClass.set("com.monkopedia.konstructor.AppKt")
}

val browser = rootProject.findProject(":frontend")!!

val debugFrontend = properties["release"] == null
val copy = tasks.register<Copy>("copyJsBundleToKtor") {
    if (debugFrontend) {
        from("${browser.buildDir}/dist/js/developmentExecutable")
    } else {
        from("${browser.buildDir}/dist/js/productionExecutable")
    }
    into("$buildDir/importedResources/web")
}
val lib = rootProject.findProject(":lib")!!
val copyLib = tasks.register<Copy>("copyLibToKtor") {
    from("${lib.buildDir}/libs/lib-all.jar")
    into("$buildDir/importedResources")
    rename { fileName: String ->
        fileName.replace(".jar", ".raj")
    }
}
afterEvaluate {

    tasks.named("copyJsBundleToKtor") {
        if (debugFrontend) {
            dependsOn(browser.tasks["jsBrowserDevelopmentWebpack"])
            mustRunAfter(browser.tasks["jsBrowserDevelopmentWebpack"])
        } else {
            dependsOn(browser.tasks["jsBrowserDistribution"])
            mustRunAfter(browser.tasks["jsBrowserDistribution"])
        }
    }
    tasks.named("copyLibToKtor") {
        dependsOn(lib.tasks["shadowJar"])
        mustRunAfter(lib.tasks["shadowJar"])
    }

    tasks.named("shadowJar") {
        mustRunAfter("copyJsBundleToKtor")
        mustRunAfter("copyLibToKtor")
    }

    tasks.named("jvmProcessResources") {
        dependsOn("copyJsBundleToKtor")
        dependsOn("copyLibToKtor")
        mustRunAfter("copyJsBundleToKtor")
        mustRunAfter("copyLibToKtor")
    }
}

sourceSets {
    main {
        afterEvaluate {
            tasks.named(processResourcesTaskName) {
                dependsOn(copy)
                dependsOn(copyLib)
            }
        }
        resources {
            srcDir("$buildDir/importedResources")
            compiledBy(copy, copyLib)
        }
    }
}
