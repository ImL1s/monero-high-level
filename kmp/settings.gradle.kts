pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "monero-kmp"

// Only include modules that don't require Android SDK for now
include(":monero-crypto")
include(":monero-core")
include(":monero-net")
// include(":monero-storage") // requires Android SDK
// include(":monero-wallet")  // requires Android SDK
