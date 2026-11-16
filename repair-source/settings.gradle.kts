pluginManagement {
    repositories {
        google()
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

rootProject.name = "Altyazi"
include(":app")
// GameLingo TV (Android TV'ye kurulan ayrı APK) yalnızca TV derleme işinde eklenir.
// Böylece TV tarafında bir sorun olsa bile telefon APK'sının derlemesi asla etkilenmez.
if (System.getenv("GAMELINGO_TV") == "1") include(":tv")
