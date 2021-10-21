plugins {
    id("org.jetbrains.kotlin.multiplatform")
    application
    id("com.monkopedia.ksrpc.plugin")
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
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0")
        implementation(project(":protocol"))
        implementation("com.github.ajalt:clikt:2.8.0")
        implementation("io.ktor:ktor-server-core:1.6.4")
        implementation("io.ktor:ktor-server-netty:1.6.4")
    }
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

val copy = tasks.register<Copy>("copyJsBundleToKtor") {
    from("${browser.buildDir}/distributions")
    into("$buildDir/processedResources/web")
}
afterEvaluate {
    val fatJar = tasks.register("fatJar", type = Jar::class) {
        baseName = "${project.name}-fat"
        manifest {
            attributes["Implementation-Title"] = "Konstructor Server"
            attributes["Implementation-Version"] = "1.0"
            attributes["Main-Class"] = application.mainClassName
        }
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        with(tasks["jar"] as CopySpec)
    }

    tasks.named("copyJsBundleToKtor") {
        dependsOn(browser.tasks["jsBrowserDistribution"])
        mustRunAfter(browser.tasks["jsBrowserDistribution"])
    }

    tasks.named("fatJar") {
        mustRunAfter("copyJsBundleToKtor")
    }
}

sourceSets {
    main {
        afterEvaluate {
            tasks.named(processResourcesTaskName) {
                dependsOn(copy)
            }
        }
        resources {
            srcDir("$buildDir/processedResources")
            compiledBy(copy)
        }
    }
}
