buildscript {
    val kotlin_version by extra("1.7.10")
    repositories {
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlin_version")
        classpath("com.monkopedia:ksrpc-gradle-plugin:0.5.5")
        classpath("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")
    }
}

allprojects {
    repositories {
        jcenter()
        mavenCentral()
        mavenLocal()
    }
}
