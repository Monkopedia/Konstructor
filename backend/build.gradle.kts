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
    id("com.monkopedia.ksrpc.plugin")
    id("com.gradleup.shadow")
}

repositories {
    mavenCentral()
    mavenLocal()
}

@OptIn(
    org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class
)
kotlin {
    jvm {
        binaries {
            executable {
                mainClass.set("com.monkopedia.konstructor.AppKt")
            }
        }
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
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.kotlinx.serialization.json)
    }
}

kotlin.compilerOptions {
    freeCompilerArgs.add("-Xskip-prerelease-check")
}

tasks.withType<
    org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
>().configureEach {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
    }
}

val browser = rootProject.findProject(":frontend")!!
val browserBuildDir = browser.layout.buildDirectory
val buildDir = layout.buildDirectory

val copy = tasks.register<Copy>("copyJsBundleToKtor") {
    from(browserBuildDir.dir("dist/wasmJs/productionExecutable"))
    into(buildDir.dir("importedResources/web"))
}
val lib = rootProject.findProject(":lib")!!
val libBuildDir = lib.layout.buildDirectory
val copyLib = tasks.register<Copy>("copyLibToKtor") {
    from(libBuildDir.file("libs/lib-all.jar"))
    into(buildDir.dir("importedResources"))
    rename { fileName: String ->
        fileName.replace(".jar", ".raj")
    }
}
afterEvaluate {

    tasks.named("copyJsBundleToKtor") {
        dependsOn(browser.tasks["wasmJsBrowserDistribution"])
        mustRunAfter(browser.tasks["wasmJsBrowserDistribution"])
    }
    tasks.named("copyLibToKtor") {
        dependsOn(lib.tasks["shadowJar"])
        mustRunAfter(lib.tasks["shadowJar"])
    }

    val jvmJar = tasks.named<Jar>("jvmJar")
    tasks.register<
        com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
    >("shadowJar") {
        from(jvmJar.map { it.outputs })
        val cp = project.configurations.getByName("jvmRuntimeClasspath")
        configurations = listOf(cp)
        archiveClassifier.set("all")
        manifest {
            attributes["Main-Class"] = "com.monkopedia.konstructor.AppKt"
        }
        mergeServiceFiles()
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

kotlin.sourceSets["jvmMain"].resources
    .srcDir(layout.buildDirectory.dir("importedResources"))
