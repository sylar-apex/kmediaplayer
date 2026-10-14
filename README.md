<div align="center">

# KMediaPlayer

**A plug-and-play media player for Kotlin Multiplatform — video, music and background playback in one call.**

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Android-Media3%20ExoPlayer-3DDC84?logo=android&logoColor=white)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20Desktop%20%7C%20Web-152438)

</div>

```kotlin
KMediaPlayer.playVideo(files, startIndex = 0)   // full-screen video player
KMediaPlayer.playMusic(tracks, startIndex = 0)  // background audio + notification
KMediaPlayer.playUrl("https://example.com/stream.m3u8")
```

## Features

**Video player**
- Gestures: swipe to seek, brightness and volume, pinch to zoom and pan, double-tap to skip, long-press for fast-forward
- Audio and subtitle tracks, external `.srt` / `.vtt` subtitles with sync and speed control
- Playback speed 0.25×–4×, A-B repeat, sleep timer, loop and shuffle
- Zoom modes and aspect ratios, lock and kids lock, night mode
- Equalizer, bass boost, reverb and volume boost up to 200%
- Picture-in-picture, background play, screenshots, bookmarks, resume where you stopped
- HW / HW+ / SW decoder switching
- Fully configurable on-screen display through a tuner (colours, layout, gestures, navigation)

**Music player**
- Mini player bar and a full Now Playing screen
- Media session with notification, lock-screen and headset controls

## Modules

| Module | Contents |
|---|---|
| `kmediaplayer:core` | `KMediaPlayer` entry point, `MediaEngine` API with a Media3 implementation on Android, background `PlaybackService`, player settings (`PlayerKeys`), `PlayerHost` hooks, subtitle parser and player logic. No Compose. |
| `kmediaplayer:ui` | Compose UI: `VideoPlayerScreen` with every panel, `MiniPlayer`, `NowPlayingScreen`, equalizer, settings rows, and `KMediaPlayerActivity` on Android |

## Setup

```bash
git submodule add https://github.com/sylar-apex/kmediaplayer.git kmediaplayer
```

```kotlin
// settings.gradle.kts
include(":kmediaplayer:core")
include(":kmediaplayer:ui")

// your module
commonMain.dependencies { api(projects.kmediaplayer.ui) }
```

On Android, initialise it once in your `Application`:

```kotlin
KMediaPlayerAndroid.init(this, videoActivity = KMediaPlayerActivity::class.java)
```

## Connect it to your app

Everything app-specific goes through `PlayerHost`. Every member has a default, so override only what you need:

```kotlin
class MyHost(private val history: HistoryStore) : PlayerHost {
    override val appName = "My Player"
    override fun playbackRecord(uri: String) = history.find(uri)
    override fun recordPlayback(file: MediaFile, positionMs: Long, durationMs: Long) = history.save(file, positionMs, durationMs)
    override suspend fun deleteFile(file: MediaFile) = myRepository.delete(file)
}

KMediaPlayer.configure(settings = myObservableSettings, host = MyHost(history))
```

To theme the player, subclass the activity:

```kotlin
class PlayerActivity : KMediaPlayerActivity() {
    @Composable
    override fun Content() = MyTheme { VideoPlayerScreen(this) }
}
```

On desktop, iOS and web, show the player inside your window with `ComposePlayerWindow` whenever `KMediaPlayer.videoRequested` is true.

## Platform status

| Platform | Playback | UI |
|---|---|---|
| Android | ✅ Media3 ExoPlayer, media session, PiP | ✅ |
| Desktop | 🚧 queue only — native engine planned | ✅ |
| iOS | 🚧 queue only — AVPlayer engine planned | ✅ |
| Web (Wasm) | 🚧 queue only — HTML video engine planned | ✅ |

## Used by

- [**MaxPlayer**](https://github.com/sylar-apex/MaxPlayer) — multiplatform video and music player
