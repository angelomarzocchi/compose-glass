pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // compose-lumen, for the demo's navigation bar in glass only: the library does not depend on it.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.angelomarzocchi") }
        }
    }
}

rootProject.name = "compose-glass"
include(":glass")
include(":demo")
