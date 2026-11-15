@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.panuszewski.typesafe-conventions") version "0.12.2"
}

rootProject.name = "build-logic"

include("conventions")
