import org.gradle.api.file.DuplicatesStrategy.INCLUDE

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.monkopedia.ksrpc.plugin")
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
val fatJar = tasks.register("fatJar", type = Jar::class) {
    duplicatesStrategy = INCLUDE
    baseName = "${project.name}-fat"
    from(
        configurations.runtimeClasspath.get().mapNotNull {
            when {
                it.absolutePath.contains("kotlin-stdlib") || it.absolutePath.contains("kotlin-reflect") -> null
                it.isDirectory -> it
                else -> zipTree(it)
            }
        }
    )
    with(tasks["jar"] as CopySpec)
}

dependencies {
    implementation("com.monkopedia:ksrpc:0.5.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
    implementation("io.ktor:ktor-io:2.0.2")
    implementation("io.ktor:ktor-client-cio:2.0.2")
    api("com.monkopedia:kcsg-lib")
    api("com.monkopedia:kcsg")
}
