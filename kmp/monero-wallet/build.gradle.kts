plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    macosArm64()
    
    // iOS targets disabled for now - requires iOS SDK
    // listOf(iosX64(), iosArm64(), iosSimulatorArm64())...

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":monero-crypto"))
                api(project(":monero-core"))
                api(project(":monero-net"))
                // api(project(":monero-storage"))  // Will add when storage is ready
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.napier)
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
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(libs.mockk)
            }
        }
    }
}
