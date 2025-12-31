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

include(":monero-crypto")
include(":monero-core")
include(":monero-net")
include(":monero-storage")
include(":monero-wallet")
