package org.app.kmediaplayer.settings

/** Grouped rows; a null title renders the rows without a header. */
data class SettingGroup(val title: String?, val items: List<Setting<*>>)

data class TunerTab(val title: String, val groups: List<SettingGroup>)

/** The Display Settings tuner tabs shown in the player overflow and in the host app's player settings. */
val tunerTabs = listOf(
    TunerTab("Style", listOf(SettingGroup(null, listOf(
        PlayerKeys.stylePreset, PlayerKeys.frameColor, PlayerKeys.controlNormalColor, PlayerKeys.controlHighlightColor,
        PlayerKeys.progressBarColor, PlayerKeys.progressBarStyle, PlayerKeys.progressBelowButtons,
    )))),
    TunerTab("Screen", listOf(
        SettingGroup(null, listOf(PlayerKeys.orientation, PlayerKeys.fullScreen, PlayerKeys.softButtons, PlayerKeys.brightnessEnabled, PlayerKeys.brightnessLevel)),
        SettingGroup("On-screen display", listOf(
            PlayerKeys.alwaysShowElapsed, PlayerKeys.alwaysShowStatus, PlayerKeys.cornerOffsetEnabled, PlayerKeys.cornerOffset,
            PlayerKeys.osdTextColor, PlayerKeys.osdBackground, PlayerKeys.osdBackColor, PlayerKeys.osdBottom,
        )),
        SettingGroup(null, listOf(
            PlayerKeys.rotationButton, PlayerKeys.batteryClockInTitle, PlayerKeys.showRemaining, PlayerKeys.keepScreenOn,
            PlayerKeys.pauseIfObscured, PlayerKeys.showInterfaceAtStartup,
        )),
    )),
    TunerTab("Controls", listOf(
        SettingGroup(null, listOf(PlayerKeys.touchAction, PlayerKeys.lockMode)),
        SettingGroup("Gestures", listOf(
            PlayerKeys.gSeek, PlayerKeys.gZoomAndPan, PlayerKeys.gZoom, PlayerKeys.gPan, PlayerKeys.gVolume, PlayerKeys.gBrightness,
            PlayerKeys.gDoubleTapPlayPause, PlayerKeys.gDoubleTapZoom, PlayerKeys.gDoubleTapFfRw, PlayerKeys.gLongPressSpeed, PlayerKeys.longPressSpeed,
            PlayerKeys.gPlaybackSpeed, PlayerKeys.gSubtitleUpDown, PlayerKeys.gSubtitleZoom,
        )),
        SettingGroup(null, listOf(PlayerKeys.interfaceAutoHide, PlayerKeys.interfaceAutoHideDelay, PlayerKeys.lockShowInterface)),
    )),
    TunerTab("Navigation", listOf(SettingGroup(null, listOf(
        PlayerKeys.seekSpeed, PlayerKeys.showMoveButtons, PlayerKeys.moveInterval, PlayerKeys.showPrevNext, PlayerKeys.displaySeekPosition,
    )))),
    TunerTab("Text", listOf(SettingGroup(null, listOf(
        PlayerKeys.subFont, PlayerKeys.subSize, PlayerKeys.subScale, PlayerKeys.subColor, PlayerKeys.subBold, PlayerKeys.subTextBackground, PlayerKeys.subTextBackgroundColor,
        PlayerKeys.subBorder, PlayerKeys.subBorderColor, PlayerKeys.subBorderThickness, PlayerKeys.subShadow, PlayerKeys.subFadeOut,
    )))),
    TunerTab("Layout", listOf(SettingGroup(null, listOf(
        PlayerKeys.subAlignment, PlayerKeys.subBottomMargin, PlayerKeys.subBackground, PlayerKeys.subBackgroundColor, PlayerKeys.subFitToVideo,
    )))),
)
