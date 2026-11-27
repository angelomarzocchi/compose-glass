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

// compose-lumen from its own sources when they sit next to this repository: the demo then runs against
// lumen as it is being changed. Elsewhere (CI, JitPack), or with -Pglass.lumenFromJitPack=true, it comes
// from JitPack.
if (file("../compose-lumen/settings.gradle.kts").exists() && providers.gradleProperty("glass.lumenFromJitPack").orNull != "true") {
    includeBuild("../compose-lumen") {
        dependencySubstitution {
            substitute(module("com.github.angelomarzocchi:compose-lumen")).using(project(":lumen"))
        }
    }
}
include(":glass")
include(":demo")
