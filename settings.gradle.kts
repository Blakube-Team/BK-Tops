pluginManagement {
    plugins {
        kotlin("jvm") version "2.1.21"
        id("com.gradleup.shadow") version "9.3.1"
        id("java-library")
        id("java")
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "BK-Tops"
include("api", "plugin")