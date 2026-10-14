import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "org.app.kmediaplayer"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.javaVersion.get())
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "kmediaplayercore"
            isStatic = true
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.media3.exoplayer)
            implementation(libs.media3.exoplayerHls)
            implementation(libs.media3.exoplayerDash)
            api(libs.media3.session)
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
