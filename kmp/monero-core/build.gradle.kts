plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    // alias(libs.plugins.android.library) // Disabled: requires Android SDK
}

kotlin {
    jvmToolchain(17)
    
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // Android target disabled - requires Android SDK
    // androidTarget { ... }

    // iOS targets disabled for now
    // listOf(iosX64(), iosArm64(), iosSimulatorArm64())...

    macosArm64 {
        binaries.framework {
            baseName = "MoneroCore"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":monero-crypto"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }

        val jvmMain by getting
        val jvmTest by getting
    }
}
