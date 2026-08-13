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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sih-android"

// sih-android multi-module layout (day1-contracts-and-repo-setup.md §4):
//   :app     → UI, navigation, DI wiring                        (Component C)
//   :relay   → Nearby Connections, epidemic routing              (Component A+B)
//   :data    → Room DB, entities, DAOs, WorkManager             (Component C)
//   :network → Retrofit client, network models matching §1/§2   (shared)
include(":app")
include(":relay")
include(":data")
include(":network")
