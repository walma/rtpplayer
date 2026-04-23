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

rootProject.name = "rtp-player"
include(":rtp-player")
include(":rtp-player-ui")

if (System.getenv("JITPACK") != "true") {
    include(":sample-app")
}
