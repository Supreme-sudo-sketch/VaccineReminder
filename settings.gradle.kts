pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Gradle 8.13 cannot run on Java 25 (Android Studio's bundled JDK). This lets Gradle use, or download,
    // the Java 21 named in gradle/gradle-daemon-jvm.properties instead.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "VaccineReminder"
include(":app")
project(":app").projectDir = file("android/app")
