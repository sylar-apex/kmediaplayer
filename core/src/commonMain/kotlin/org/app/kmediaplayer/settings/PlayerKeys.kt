package org.app.kmediaplayer.settings

import org.app.kmediaplayer.logic.AspectRatio

/** Quick shortcut buttons on the player, in the reference app's expanded-row order. */
enum class Shortcut(val label: String) {
    NightMode("Night Mode"), Customise("Customise Items"), Shuffle("Shuffle"), Loop("Loop"), Mute("Mute"),
    SleepTimer("Sleep Timer"), AbRepeat("A - B Repeat"), Equalizer("Equalizer"), Speed("Speed"),
    Screenshot("Screenshot"), BackgroundPlay("Background Play"), Rotation("Screen Rotation");

    companion object {
        /** Shown before the expand chevron when the row is collapsed. */
        val collapsed = listOf(Equalizer, Speed, Screenshot, BackgroundPlay, Rotation)

        fun decode(raw: String): List<Shortcut> = raw.split(',').mapNotNull { n -> entries.firstOrNull { it.name == n } }
    }
}

enum class OrientationMode(val label: String) {
    Video("Use video orientation"), System("System default"), Sensor("Auto rotation"),
    Landscape("Landscape"), ReverseLandscape("Landscape (reverse)"), Portrait("Portrait"),
}

/** Every player preference: playback, background play, on-screen display, gestures, decoder, audio and subtitles. */
object PlayerKeys : SettingRegistry() {
    val accentColor = r(ColorSetting("accent_color", "Accent colour", 0xFFE0E0E0))

    // ---- Player: interface ----
    val doubleTapBack = r(BoolSetting("double_tap_back_key", "Double-tap the back button", false, "Press the back button twice to close playback screen."))
    val quickZoom = r(BoolSetting("quick_zoom", "Quick zoom", true, "Skip zoom steps between 100% and 'fit to screen' (150%, 200%, etc)."))

    // ---- Player: playback ----
    val resume = r(ChoiceSetting("resume_last", "Resume", "resume", listOf("ask" to "Ask at startup", "resume" to "Resume", "start_over" to "Start over"),
        "Select whether to resume from the point where you stopped."))
    val resumeOnlyFirst = r(BoolSetting("resume_only_first", "Resume only the first file", false, "Apply resume setting only on the first file. Next files will start from the beginning."))
    val defaultSpeed = r(IntSetting("default_playback_speed", "Default playback speed", 100, 25..400, unit = "%"))
    val rememberSelections = r(BoolSetting("remember_selections", "Remember selections", true, "Remember selections for each file such as choice of audio track, subtitle track, decoder…"))
    val backToList = r(BoolSetting("back_to_list", "Back to list", false, "Return to the list after playback is completed."))
    val seekPreviews = r(BoolSetting("seek_previews", "Preview while seeking", false, "Display preview image while changing playback position."))
    val fastSeek = r(BoolSetting("fast_seek", "Fast seeking", true, "Seek to nearest key-frame position instead of exact position."))
    val playAlone = r(BoolSetting("audio_focus", "Play alone", true, "Stop other players while playing back videos or audios."))
    val mediaButtons = r(BoolSetting("media_buttons", "Media buttons", true, "Respond to media control buttons from headset, Bluetooth, etc."))
    val multiPressNextPrev = r(BoolSetting("honour_headset_hook_multi_press", "Double/Triple Press → Next/Prev", true, "Treat Double/Triple press events as Next/Prev"))
    val prevNextToRewFf = r(BoolSetting("prevnext_to_rewff", "Next/Prev → FF/Rew", false, "Treat Next/Prev media buttons as FF/Rew button."))
    val smartPrev = r(BoolSetting("smart_prev", "Smart Previous Button", true, "If you select 'previous' button, it will resume again from the beginning or open the previous file."))
    val suppressError = r(BoolSetting("suppress_error_message", "Suppress error message", false, "Skip to next video without displaying error message if video loading fails."))

    // ---- Player: background ----
    val backgroundMode = r(ChoiceSetting("sticky_video", "Background/PiP mode", "pip", listOf("stop" to "Stop", "background" to "Background play", "pip" to "Picture-in-Picture"),
        "Allows you to control the player behaviour when switching to another app."))
    val backgroundAudio = r(BoolSetting("sticky_audio", "Background play (audio)", true, "Use Background play for audio playback."))
    val albumArt = r(BoolSetting("album_art", "Album art", true, "Display cover art on the lock screen."))
    val smoothSwitch = r(BoolSetting("sticky_smooth_switch", "Smooth switch", false, "Fade in and out audio when switching to and return back from background play."))

    // ---- Player: misc ----
    val defaultAspect = r(ChoiceSetting("player.default_aspect", "Aspect Ratio", AspectRatio.Default.name, AspectRatio.entries.filter { it != AspectRatio.Custom }.map { it.name to it.label }))
    val loadingCircle = r(BoolSetting("loading_circle_animation", "Loading circle animation", true))

    // ---- Tuner: Style ----
    val stylePreset = r(ChoiceSetting("osd.preset", "Preset", "default", listOf("default" to "Default", "classic" to "Classic", "minimal" to "Minimal")))
    val frameColor = r(ColorSetting("osd.frame_color", "Frame", 0x59000000))
    val controlNormalColor = r(ColorSetting("osd.control_normal", "Controls", 0xFFFFFFFF))
    val controlHighlightColor = r(ColorSetting("osd.control_highlight", "Controls (highlight)", 0xFFE0E0E0))
    val progressBarColor = r(ColorSetting("osd.progress_color", "Progress bar", 0xFFE0E0E0))
    val progressBarStyle = r(ChoiceSetting("osd.progress_style", "Progress bar style", "material", listOf("material" to "Material", "flat" to "Flat", "thin" to "Thin")))
    val progressBelowButtons = r(BoolSetting("osd.progress_below", "Place progress bar below buttons.", false))

    // ---- Tuner: Screen ----
    val orientation = r(ChoiceSetting("screen_orientation", "Orientation", OrientationMode.Video.name, OrientationMode.entries.map { it.name to it.label }))
    val fullScreen = r(ChoiceSetting("full_screen", "Full Screen", "auto", listOf("auto" to "Auto Switch", "on" to "On", "off" to "Off")))
    val softButtons = r(ChoiceSetting("soft_buttons", "Soft buttons", "auto_hide", listOf("auto_hide" to "Auto hide", "show" to "Show", "hide" to "Hide")))
    val brightnessEnabled = r(BoolSetting("screen_brightness_enabled", "Brightness", true))
    val brightnessLevel = r(IntSetting("screen_brightness", "Brightness level", 24, 0..30))
    val alwaysShowElapsed = r(BoolSetting("osd.always_elapsed", "Elapsed time", false))
    val alwaysShowStatus = r(BoolSetting("osd.always_status", "Battery/clock", false))
    val cornerOffsetEnabled = r(BoolSetting("corner_offset_enable", "Corner offset", false))
    val cornerOffset = r(IntSetting("corner_offset", "Corner offset size", 0, 0..40))
    val osdTextColor = r(ColorSetting("osd.text_color", "Text", 0xFFFFFFFF))
    val osdBackground = r(BoolSetting("osd.background", "Background", true))
    val osdBackColor = r(ColorSetting("osd.back_color", "Background colour", 0x99000000))
    val osdBottom = r(BoolSetting("osd.bottom", "Place at the bottom", false))
    val rotationButton = r(BoolSetting("screen_rotation_button", "Screen rotation button", true))
    val batteryClockInTitle = r(BoolSetting("battery_clock_in_title_bar", "Display battery/clock in title bar", false))
    val showRemaining = r(BoolSetting("show_left_time", "Show Remaining Time", true))
    val keepScreenOn = r(BoolSetting("keep_screen_on", "Keep Screen On", false, "Keep the screen on even while paused."))
    val pauseIfObscured = r(BoolSetting("pause_if_obscured", "Pause playback if obstructed by another window", false))
    val showInterfaceAtStartup = r(BoolSetting("show_interface_at_startup", "Show interface at the startup", false))

    // ---- Tuner: Controls ----
    val touchAction = r(ChoiceSetting("touch_action", "Touch action", "toggle", listOf("toggle" to "Show/hide interface", "pause" to "Pause/resume", "both" to "Show interface + pause")))
    val lockMode = r(ChoiceSetting("lock_mode", "Lock mode", "lock", listOf("lock" to "Lock", "kids" to "Kids lock")))
    val gSeek = r(BoolSetting("g.seek", "Seek position", true))
    val gZoomAndPan = r(BoolSetting("g.zoom_and_pan", "Zoom and Pan", false))
    val gZoom = r(BoolSetting("g.zoom", "Video zoom", true))
    val gPan = r(BoolSetting("g.pan", "Video pan", false))
    val gVolume = r(BoolSetting("g.volume", "Volume", true))
    val gBrightness = r(BoolSetting("g.brightness", "Brightness", true))
    val gDoubleTapPlayPause = r(BoolSetting("g.dt_play_pause", "Play/Pause (Double tap)", true))
    val gDoubleTapZoom = r(BoolSetting("g.dt_zoom", "Video zoom (Double tap)", false))
    val gDoubleTapFfRw = r(BoolSetting("g.dt_ffrw", "FF/RW (Double tap)", true))
    val gLongPressSpeed = r(BoolSetting("g.long_press_speed", "Speed FF (Long press)", true))
    val longPressSpeed = r(FloatSetting("long_press_speed_value", "Speed FF value", 2.0f, 1.25f..4f, unit = "x"))
    val gPlaybackSpeed = r(BoolSetting("g.playback_speed", "Playback Speed", true))
    val gSubtitleUpDown = r(BoolSetting("g.subtitle_updown", "Subtitle up/down", true))
    val gSubtitleZoom = r(BoolSetting("g.subtitle_zoom", "Subtitle zoom", true))
    val interfaceAutoHide = r(BoolSetting("interface_auto_hide", "Interface auto hide", true))
    val interfaceAutoHideDelay = r(FloatSetting("interface_auto_hide_delay", "Auto hide delay", 2.0f, 1f..10f, unit = "sec"))
    val lockShowInterface = r(BoolSetting("lock_show_interface", "Show interface when locked screen is touched", false))
    val shortcutsVisible = r(BoolSetting("osd.shortcuts", "Shortcuts", true))
    val shortcutItems = r(TextSetting("osd.shortcut_items", "Quick Shortcuts", Shortcut.entries.joinToString(",") { it.name }))
    val videoDisplay = r(BoolSetting("osd.video_display", "Video Display", true))

    // ---- Tuner: Navigation ----
    val seekSpeed = r(IntSetting("gesture_seek_speed", "Seek Speed (sec/cm)", 10, 1..60))
    val showMoveButtons = r(BoolSetting("show_move_buttons", "Forward/backward moving button", false))
    val moveInterval = r(IntSetting("move_interval", "Move Interval (sec.)", 10, 1..120))
    val showPrevNext = r(BoolSetting("show_prev_next", "Previous/next button", true))
    val displaySeekPosition = r(BoolSetting("display_seeking_position", "Display the current position while changing position", true))

    // ---- Decoder ----
    val defaultDecoder = r(ChoiceSetting("default_decoder", "Default decoder", "hw", listOf("hw" to "HW decoder", "hwplus" to "HW+ decoder", "sw" to "SW decoder")))

    // ---- Audio ----
    val audioPlayer = r(BoolSetting("audio_player", "Audio player", false, "Use as audio player."))
    val volumeBoost = r(BoolSetting("volume_boost", "Volume boost", true, "Audio volume can be boosted up to 200%."))
    val syncSystemVolume = r(BoolSetting("sync_system_volume", "System volume", true, "Synchronize sound volume with the system media volume."))
    val pauseOnHeadsetDisconnect = r(BoolSetting("pause_on_headset_disconnected", "Pause on headset disconnected", true, "Pause playback when wired/Bluetooth headset disconnected."))
    val fadeOnStart = r(BoolSetting("audio_fade_in_on_start", "Fade on start", true))
    val fadeOnSeek = r(BoolSetting("audio_fade_in_on_seek", "Fade on seek", true))
    val audioLanguage = r(TextSetting("audio_language", "Preferred audio language", "", "Language of the audio track you want to use (ISO code, e.g. hi, en)."))
    val audioDelay = r(FloatSetting("audio_delay", "Audio delay", 0f, -10f..10f, "Set audio delay time for audio synchronization.", "sec"))
    val equalizerEnabled = r(BoolSetting("audio_effects_enabled", "Audio equalizer", false))
    val equalizerPreset = r(IntSetting("eq.preset", "Equalizer preset", -1, -1..64))
    val equalizerBands = r(TextSetting("eq.bands", "Equalizer bands", ""))
    val bassBoost = r(IntSetting("eq.bass_boost", "Bass Boost", 0, 0..1000))
    val reverb = r(IntSetting("eq.reverb", "Reverb", 0, 0..6))

    // ---- Subtitle ----
    val subtitleFolder = r(TextSetting("subtitle_folder", "Subtitle Folder", "", "A folder to search for subtitles in addition to the video folder."))
    val subtitleCharset = r(ChoiceSetting("subtitle_charset", "Character encoding", "", listOf("" to "Auto detect", "UTF-8" to "UTF-8", "ISO-8859-1" to "Western (ISO-8859-1)", "windows-1256" to "Arabic (Windows-1256)", "windows-1251" to "Cyrillic (Windows-1251)")))
    val subtitleLanguage = r(TextSetting("subtitle_language", "Preferred subtitle language", "", "Language of the subtitle track you want to use (ISO code)."))
    val subtitleDefaultSync = r(FloatSetting("subtitle_default_sync", "Default sync", 0f, -60f..60f, "Set default time adjustment for subtitle synchronization.", "sec"))
    val italicTag = r(BoolSetting("italic_tag", "Italic effect", false, "Sentences that start with \"/\" will be displayed in italics."))
    val forceLtr = r(BoolSetting("force_ltr", "Force LTR direction", true, "Force LTR (Left to Right) direction for RTL subtitles."))
    val subFont = r(ChoiceSetting("sub.font", "Font", "default", listOf("default" to "Default", "sans" to "Sans serif", "serif" to "Serif", "mono" to "Monospace")))
    val subSize = r(IntSetting("sub.size", "Size", 19, 8..60))
    val subScale = r(IntSetting("sub.scale", "Scale", 100, 50..300, unit = "%"))
    val subColor = r(ColorSetting("sub.color", "Color", 0xFFFFFFFF))
    val subBold = r(BoolSetting("sub.bold", "Bold", true))
    val subTextBackground = r(BoolSetting("sub.text_bg", "Background Color", false))
    val subTextBackgroundColor = r(ColorSetting("sub.text_bg_color", "Background Color", 0x80000000))
    val subBorder = r(BoolSetting("sub.border", "Border", false))
    val subBorderColor = r(ColorSetting("sub.border_color", "Border colour", 0xFF000000))
    val subBorderThickness = r(IntSetting("sub.border_thickness", "Border thickness", 80, 10..300, unit = "%"))
    val subShadow = r(BoolSetting("sub.shadow", "Shadow", true))
    val subFadeOut = r(BoolSetting("sub.fadeout", "Fade out", true))
    val subAlignment = r(ChoiceSetting("sub.alignment", "Alignment", "center", listOf("left" to "Left", "center" to "Center", "right" to "Right")))
    val subBottomMargin = r(IntSetting("sub.bottom_margin", "Bottom margins", 8, 0..50))
    val subBackground = r(BoolSetting("sub.background", "Background", false))
    val subBackgroundColor = r(ColorSetting("sub.background_color", "Background colour", 0x80000000))
    val subFitToVideo = r(BoolSetting("sub.fit_video", "Fit subtitles into video size", true))

    // ---- Network ----
    val playMediaLinks = r(BoolSetting("play_video_links", "Play media links", true, "Play HTTP/HTTPS media links opened from other apps."))
    val userAgent = r(TextSetting("http_user_agent", "Http User-Agent", "", "Override 'User-Agent' on connecting to web server. Leave blank to use default value."))
}
