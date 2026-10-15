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
            implementation(libs.media3.ffmpeg)
            implementation(libs.libvlc.android)
            implementation(libs.androidx.core.ktx)
        }
        val desktopMain by getting {
            dependencies { implementation(libs.vlcj) }
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Optional libVLC integration test: `-PvlcPath=<libvlc dir> -PprobeClips=<clips folder>`.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("vlcPath").orNull?.let { systemProperty("vlc.path", it) }
    providers.gradleProperty("probeClips").orNull?.let { systemProperty("probe.clips", it) }
}
