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
    jcenter()
}

kotlin {
    jvm {
        withJava()
    }
    sourceSets["jvmMain"].dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3-native-mt")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.3-native-mt")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
        implementation(project(":protocol"))
        implementation(project(":lib"))
        implementation("com.github.ajalt:clikt:2.8.0")
        implementation("io.ktor:ktor-server-core:2.0.2")
        implementation("io.ktor:ktor-server-cors:2.0.2")
        implementation("io.ktor:ktor-server-websockets:2.0.2")
        implementation("io.ktor:ktor-server-status-pages:2.0.2")
        implementation("io.ktor:ktor-server-netty:2.0.2")
        implementation("io.ktor:ktor-websocket-serialization:2.0.2")
    }
    sourceSets["jvmTest"].dependencies {
        implementation(kotlin("test-junit"))
    }
}

dependencies {
    implementation(project(":lib"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-Xskip-prerelease-check"
    }
}

application {
    mainClassName = "com.monkopedia.konstructor.AppKt"
}

val browser = rootProject.findProject(":frontend")!!

val debugFrontend = true
val copy = tasks.register<Copy>("copyJsBundleToKtor") {
    if (debugFrontend) {
        from("${browser.buildDir}/developmentExecutable")
    } else {
        from("${browser.buildDir}/distributions")
    }
    into("$buildDir/processedResources/web")
}
val lib = rootProject.findProject(":lib")!!
val copyLib = tasks.register<Copy>("copyLibToKtor") {
    from("${lib.buildDir}/libs/lib-all.jar")
    into("$buildDir/processedResources/")
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
            srcDir("$buildDir/processedResources")
            compiledBy(copy, copyLib)
        }
    }
}
