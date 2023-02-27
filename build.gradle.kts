buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
    }
}

plugins {
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("org.jetbrains.dokka") version "1.6.21"
    id("org.jetbrains.kotlin.jvm") version "1.6.21" apply false
}

apply(from = "${rootDir}/scripts/publish-root.gradle")

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}