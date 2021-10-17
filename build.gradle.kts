plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.31"

    application
}

repositories {
    jcenter()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
buildscript {
    val kotlin_version by extra("1.5.10")
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
    }
}

application {
    // Define the main class for the application.
    mainClassName = "com.monkopedia.konstructor.AppKt"
}
