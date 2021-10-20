plugins {
    application
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
}

repositories {
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")
    implementation(project(":common"))
    implementation("com.github.ajalt.clikt:clikt:3.2.0")
    implementation("io.ktor:ktor-server-core:1.6.4")
    implementation("io.ktor:ktor-server-netty:1.6.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

buildscript {
    val kotlin_version by extra("1.5.31")
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("com.monkopedia:ksrpc-gradle-plugin:0.4.2")
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
        mustRunAfter(browser.tasks["jsBrowserProductionWebpack"])
    }

    tasks.named("fatJar") {
        mustRunAfter("copyJsBundleToKtor")
    }
}

sourceSets {
    main {
        resources {
            srcDir("$buildDir/processedResources")
            compiledBy(copy)
        }
    }
}
