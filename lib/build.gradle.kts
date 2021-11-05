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
    implementation("com.monkopedia:ksrpc:0.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("io.ktor:ktor-io:1.6.3")
}
