buildscript {
    val kotlin_version by extra("1.7.10")
    repositories {
        mavenCentral()
        mavenLocal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlin_version")
        classpath("com.monkopedia:ksrpc-gradle-plugin:0.5.5")
    }
}

allprojects {
    repositories {
        jcenter()
        mavenCentral()
        mavenLocal()
    }
}
