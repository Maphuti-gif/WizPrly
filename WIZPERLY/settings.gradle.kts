pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://download.jetbrains.com/kotlin/native/builds") }
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://download.jetbrains.com/kotlin/native/builds") }
    }
}
rootProject.name = "WizPrly"
include(":app")
include(":shared")
