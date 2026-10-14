import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "org.app.kmediaplayer.ui.resources"
}

kotlin {
    android {
        namespace = "org.app.kmediaplayer.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget(libs.versions.javaVersion.get())
        }
        androidResources {
            enable = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "kmediaplayerui"
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
            api(projects.kmediaplayer.core)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
