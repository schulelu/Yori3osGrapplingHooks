// Standalone build for the Paper port. The Fabric/NeoForge build in the repository root is not involved.
// Build from the repository root with: ./gradlew -p paper build
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "YoHooks-Paper"
