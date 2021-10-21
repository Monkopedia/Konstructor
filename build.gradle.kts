buildscript {
    val kotlin_version by extra("1.5.31")
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlin_version")
        classpath("com.monkopedia:ksrpc-gradle-plugin:0.4.2")
    }
}

