pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://download.jetbrains.com/kotlin/native/builds") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://download.jetbrains.com/kotlin/native/builds/releases/1.9.22/windows-x86_64") }
        maven { url = uri("https://download.jetbrains.com/kotlin/native/builds") }
    }
}
rootProject.name = "WizPrly"
include(":app")
include(":shared")
