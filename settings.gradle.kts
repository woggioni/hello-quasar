pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        jcenter()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.4.21" apply false
    }
}

rootProject.name = "hello-quasar"

include("suspendables")
