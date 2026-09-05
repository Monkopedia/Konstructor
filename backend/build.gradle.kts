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
import java.io.File
import java.time.Duration
import java.util.zip.ZipFile

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
        // DefaultLanguageServer + LSP types for the stub LSP server.
        implementation(libs.lsp.ksrpc)
        implementation(libs.clikt)
        implementation(libs.ktor.server.core)
        implementation(libs.ktor.server.compression)
        implementation(libs.ktor.server.caching.headers)
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
        // In-memory duplex ksrpc Connection for the LSP sub-service leak test
        // (open+close the nested lsp() sub-service N× over a real bidi channel and
        // assert the host channel's sub-service count returns to baseline — #40).
        implementation(libs.ksrpc.sockets)
    }
}

kotlin.compilerOptions {
    freeCompilerArgs.add("-Xskip-prerelease-check")
}

// Forward opt-in flags to the test JVM. Gradle does not propagate -D system
// properties or -P project properties to forked test workers automatically, so
// without this the `integration`/`soak` gates always read null and every gated
// test self-skips. Supports either `-Dintegration=true` or `-Pintegration`.
tasks.withType<Test>().configureEach {
    listOf("integration", "soak", "duration").forEach { key ->
        val value = (project.findProperty(key) as? String)
            ?: System.getProperty(key)
        if (value != null) {
            systemProperty(key, value)
        }
    }
    // Soak tests intentionally run long; never let Gradle's default timeout
    // (or a stuck subprocess) kill the worker silently.
    if ((project.findProperty("soak") as? String) != null ||
        System.getProperty("soak") != null
    ) {
        timeout.set(Duration.ofHours(2))
    }
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

// Set -PwasmDev=true to use the development bundle (unoptimized, preserves
// function names, much larger) for easier debugging of wasm runtime errors.
val useDevBundle = (project.findProperty("wasmDev") as? String)?.toBoolean() == true
val bundleDir = if (useDevBundle) "developmentExecutable" else "productionExecutable"
val bundleTask = if (useDevBundle) {
    "wasmJsBrowserDevelopmentExecutableDistribution"
} else {
    "wasmJsBrowserDistribution"
}

val copy = tasks.register<Copy>("copyJsBundleToKtor") {
    from(browserBuildDir.dir("dist/wasmJs/$bundleDir"))
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
// ─────────────────────────────────────────────────────────────────────────────
// Packaging guards for `backend-all.jar` (#137)
//
// The Shadow 8.3.6 -> 9.6.1 migration (#64 / PR #90) shipped TWO artifact
// regressions that passed the full build, the unit tests, the integration tests
// AND e2e. Both were caught only because a human unzipped the jar and diffed it
// against a baseline. Nothing in the repo would have caught either one, and the
// next Shadow bump has exactly the same exposure. These checks are that missing
// automation:
//
//   * no nested `*.jar` entries   -> defect 1 (44.9 MB -> 63.9 MB opaque copy)
//   * every multi-contributor `META-INF/services/*` file is fully merged
//                                 -> defect 2 (merger silently stopped merging)
//
// ⚠️ MORDANT IS THE POSITIVE CONTROL FOR THE SERVICE-FILE MERGE. ⚠️
// Of the 87 jars on `jvmRuntimeClasspath`, exactly one `META-INF/services/*`
// file has more than one contributor:
// `com.github.ajalt.mordant.terminal.TerminalInterfaceProvider`, contributed by
// mordant-jvm-jna, mordant-jvm-ffm and mordant-jvm-graal-ffi (pulled in by
// `libs.clikt`). Every other service file on the classpath has a single
// contributor and comes out BYTE-IDENTICAL from a completely dead merger —
// which is why the "diff the SLF4J service file" check originally proposed for
// #64 is a false negative and passes on a broken jar.
//
// So: if clikt/mordant is ever dropped, this guard loses the only input that
// can make it fail. That must not be silent. The check below FAILS when the
// classpath has no multi-contributor service file at all, rather than passing
// vacuously over an empty set. If you are removing mordant, you are removing
// the merge guard's positive control — replace it or accept that #64 defect 2
// becomes undetectable again.
// ─────────────────────────────────────────────────────────────────────────────

// Shadow's own ServiceFileTransformer skips this path, so it is legitimately
// unmerged. No Groovy on this classpath today; mirroring the exclusion keeps the
// guard from inventing a failure if one ever arrives. (Noted in the #90 review.)
val unmergedServicePaths = setOf(
    "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
)

// A service file is a newline-separated list of implementation class names,
// with `#` comments and blank lines allowed (java.util.ServiceLoader spec).
fun serviceProviders(text: String): List<String> = text.lineSequence()
    .map { it.substringBefore('#').trim() }
    .filter { it.isNotEmpty() }
    .toList()

fun readServiceFiles(jar: File): Map<String, List<String>> = ZipFile(jar).use { zip ->
    zip.entries().asSequence()
        .filter { !it.isDirectory && it.name.startsWith("META-INF/services/") }
        .filter { it.name !in unmergedServicePaths }
        .mapNotNull { entry ->
            val providers = serviceProviders(
                zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            )
            if (providers.isEmpty()) null else entry.name to providers
        }
        .toMap()
}

fun checkPackagedJar(archive: File, runtimeClasspath: Collection<File>) {
    val problems = mutableListOf<String>()

    // What the inputs offer: service path -> (contributing jar -> providers).
    val contributed = linkedMapOf<String, MutableMap<String, List<String>>>()
    runtimeClasspath.filter { it.isFile && it.name.endsWith(".jar") }.forEach { jar ->
        readServiceFiles(jar).forEach { (path, providers) ->
            contributed.getOrPut(path) { linkedMapOf() }[jar.name] = providers
        }
    }
    val multiContributor = contributed.filterValues { it.size > 1 }

    if (multiContributor.isEmpty()) {
        problems += "POSITIVE CONTROL GONE: no META-INF/services file on " +
            "jvmRuntimeClasspath has more than one contributor, so this guard can " +
            "no longer detect a broken service-file merge (#64 defect 2) at all. " +
            "Mordant's TerminalInterfaceProvider used to be that control — if you " +
            "just removed clikt/mordant, add another multi-contributor service " +
            "file or accept that the regression becomes undetectable."
    }

    ZipFile(archive).use { zip ->
        // Defect 1: Shadow 9's `from(jvmJar)` copies a jar as an opaque FILE.
        // `lib-all.raj` is the one deliberately-embedded archive and is renamed
        // precisely so it is not mistaken for classpath content.
        val nested = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".jar", ignoreCase = true) }
            .map { it.name }
            .toList()
        if (nested.isNotEmpty()) {
            problems += "nested jar entries in ${archive.name} that no classloader " +
                "reads (#64 defect 1): $nested"
        }

        // Defect 2: every service file with >1 contributor must come out merged.
        multiContributor.forEach { (path, byJar) ->
            val expected = byJar.values.flatten().distinct()
            val entry = zip.getEntry(path)
            if (entry == null) {
                problems += "$path is missing from ${archive.name}; expected the " +
                    "merge of ${byJar.keys}"
                return@forEach
            }
            val actual = serviceProviders(
                zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            )
            val missing = expected - actual.toSet()
            if (missing.isNotEmpty()) {
                problems += "$path was NOT merged (#64 defect 2): packaged " +
                    "${actual.size} of ${expected.size} providers, missing " +
                    "$missing. Contributed by ${byJar.keys}."
            }
        }
    }

    if (problems.isNotEmpty()) {
        throw GradleException(
            "${archive.name} failed its packaging guards (#137):\n" +
                problems.joinToString("\n") { "  - $it" }
        )
    }
    logger.lifecycle(
        "verifyShadowJarPackaging: ${archive.name} OK - no nested jars, " +
            "${multiContributor.size} multi-contributor service file(s) merged " +
            multiContributor.entries.joinToString(prefix = "(", postfix = ")") {
                "${it.key.substringAfterLast('.')}: " +
                    "${it.value.values.flatten().distinct().size} providers " +
                    "from ${it.value.size} jars"
            }
    )
}

// Always re-verify: the check is a couple of seconds of zip reading, and a guard
// that can go UP-TO-DATE is a guard that can be absent when it matters.
val verifyShadowJarPackaging = tasks.register("verifyShadowJarPackaging") {
    group = "verification"
    description = "Assert backend-all.jar has no nested jars and that every " +
        "multi-contributor META-INF/services file was actually merged (#137)."
    dependsOn("shadowJar")
    outputs.upToDateWhen { false }
    doLast {
        val shadow = tasks.named<
            com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
            >("shadowJar").get()
        checkPackagedJar(
            shadow.archiveFile.get().asFile,
            configurations.getByName("jvmRuntimeClasspath").files
        )
    }
}

afterEvaluate {

    tasks.named("copyJsBundleToKtor") {
        dependsOn(browser.tasks[bundleTask])
        mustRunAfter(browser.tasks[bundleTask])
    }
    tasks.named("copyLibToKtor") {
        dependsOn(lib.tasks["shadowJar"])
        mustRunAfter(lib.tasks["shadowJar"])
    }

    // Shadow 9.x auto-registers a `shadowJar` task for this KMP-jvm module (8.x did
    // not), so `tasks.register("shadowJar")` collides with "task already exists".
    // Configure the plugin-created task instead. That task already pulls in the jvm
    // main output, so the old explicit `from(jvmJar.map { it.outputs })` is not only
    // redundant, it is harmful under 9.x: `ShadowJar.from` was aligned with Gradle's
    // `AbstractCopyTask.from`, which copies a jar as an OPAQUE FILE rather than
    // unzipping it — that added an 18.8 MB `backend-jvm-0.3.0.jar` entry inside
    // `backend-all.jar` (44.9 MB -> 63.9 MB) that nothing on the classpath can read.
    tasks.named<
        com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
        >("shadowJar") {
        val cp = project.configurations.getByName("jvmRuntimeClasspath")
        configurations = listOf(cp)
        archiveClassifier.set("all")
        // Keep the output name `backend-all.jar` stable regardless of the project
        // version (set in gradle.properties). Downstream consumers reference a fixed
        // name: e2e launches `libs/backend-all.jar` and the adolin deploy expects it.
        // Without this, version=0.3.0 produces `backend-0.3.0-all.jar` and e2e's
        // -Dkonstructor.jar path breaks (every e2e test times out at server startup).
        archiveVersion.set("")
        manifest {
            attributes["Main-Class"] = "com.monkopedia.konstructor.AppKt"
        }
        // Shadow 9.x actually honours `duplicatesStrategy`, and its default is
        // EXCLUDE — which drops a duplicate entry BEFORE any transformer sees it, so
        // `mergeServiceFiles()` silently degrades to "keep the first jar's file".
        // Measured on this project: `META-INF/services/...TerminalInterfaceProvider`
        // collapsed from 3 providers to 1. Let service files through as duplicates so
        // the ServiceFileTransformer can actually merge them; everything else keeps
        // the EXCLUDE default. (#64)
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        mergeServiceFiles()
        // Guard 1 (#137). `duplicatesStrategy = INCLUDE` above lets duplicate
        // service files reach the ServiceFileTransformer, which is the only thing
        // that then collapses them back into one entry. If that merger is ever
        // removed or stops matching, the INCLUDE silently writes the SAME path
        // several times into the archive and the JVM's ServiceLoader reads
        // whichever copy it hits first. Shadow can fail the build on that instead
        // of shipping it. Verified red: deleting `mergeServiceFiles()` above makes
        // `:backend:shadowJar` fail with 3 duplicate entries.
        failOnDuplicateEntries.set(true)
        finalizedBy(verifyShadowJarPackaging)
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
