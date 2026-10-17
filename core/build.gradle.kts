import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.nio.file.Files
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * MobileVLCKit (libVLC for iOS) is VideoLAN's prebuilt xcframework. It is downloaded once into the Gradle cache
 * and unpacked to `iosApp/Frameworks`, where both the Kotlin cinterop and the Xcode app link it.
 */
val vlcKitVersion = "3.7.3"
val vlcKitArchive = "MobileVLCKit-3.7.3-319ed2c0-79128878.tar.xz"
val vlcKitDir = rootProject.layout.projectDirectory.dir("iosApp/Frameworks/MobileVLCKit.xcframework")

abstract class PrepareVlcKit @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Internal abstract val cacheFile: RegularFileProperty
    @get:OutputDirectory abstract val frameworkDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = frameworkDir.get().asFile
        if (File(out, "Info.plist").exists()) return
        val archive = cacheFile.get().asFile
        if (!archive.exists()) {
            archive.parentFile.mkdirs()
            logger.lifecycle("Downloading ${url.get()}")
            val tmp = File(archive.path + ".part")
            URI(url.get()).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(archive)
        }
        val unpack = Files.createTempDirectory("vlckit").toFile()
        exec.exec { commandLine("tar", "-xf", archive.path, "-C", unpack.path) }
        out.parentFile.mkdirs()
        exec.exec { commandLine("ditto", File(unpack, "MobileVLCKit-binary/MobileVLCKit.xcframework").path, out.path) }
        unpack.deleteRecursively()
    }
}

val prepareVlcKit by tasks.registering(PrepareVlcKit::class) {
    url.set("https://download.videolan.org/pub/cocoapods/prod/$vlcKitArchive")
    cacheFile.set(File(gradle.gradleUserHomeDir, "caches/vimate-vlc/$vlcKitArchive"))
    frameworkDir.set(vlcKitDir)
}
tasks.matching { it.name.startsWith("cinteropMobileVLCKit") }.configureEach { dependsOn(prepareVlcKit) }

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
        iosArm64() to "ios-arm64_armv7_armv7s",
        iosSimulatorArm64() to "ios-arm64_i386_x86_64-simulator",
    ).forEach { (target, slice) ->
        target.binaries.framework {
            baseName = "kmediaplayercore"
            isStatic = true
        }
        // libVLC for iOS (VideoLAN's MobileVLCKit), bound to Kotlin; the app links the same framework.
        target.compilations.getByName("main").cinterops.create("MobileVLCKit") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/MobileVLCKit.def"))
            compilerOpts("-F${vlcKitDir.asFile}/$slice")
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
