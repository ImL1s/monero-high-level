plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    // alias(libs.plugins.android.library) // Disabled: requires Android SDK
}

kotlin {
    jvmToolchain(17)
    
    // JVM
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // macOS
    macosArm64 {
        binaries.framework {
            baseName = "MoneroCrypto"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.property)
            }
        }

        val jvmMain by getting
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit5)
                implementation(libs.mockk)
            }
        }

        val macosArm64Main by getting
        val macosArm64Test by getting
    }
}
