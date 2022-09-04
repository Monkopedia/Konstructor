import org.gradle.api.file.DuplicatesStrategy.INCLUDE

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
    implementation("com.monkopedia:ksrpc:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
    implementation("io.ktor:ktor-io:2.0.2")
    implementation("io.ktor:ktor-client-cio:2.0.2")
    api("com.monkopedia:kcsg-lib")
    api("com.monkopedia:kcsg")
}
