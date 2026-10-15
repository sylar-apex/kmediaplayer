package org.app.kmediaplayer.ui.video

import org.app.kmediaplayer.settings.Shortcut

/**
 * What the player can really do on this platform. Controls for anything else are hidden rather
 * than shown and then failing (for example no equalizer in browsers, no screen rotation on desktop).
 */
expect object PlayerCapabilities {
    /** Equalizer / bass boost / reverb on the audio output. */
    val equalizer: Boolean
    /** Listing and switching the file's audio tracks. */
    val audioTrackSelection: Boolean
    /** Choosing hardware or software decoding. */
    val decoderChoice: Boolean
    /** Keep playing audio after leaving the player screen. */
    val backgroundPlay: Boolean
    /** Rotating the device screen (phones and tablets). */
    val screenRotation: Boolean
    val pictureInPicture: Boolean
    /** A maximise / restore button (desktop windows, browser full screen). */
    val fullscreenButton: Boolean
    /** Keyboard shortcuts (space, arrows, ...). */
    val keyboardShortcuts: Boolean
    /** Deleting and renaming the file being played. */
    val fileEditing: Boolean
    /** An app store page to check for updates. */
    val storeUpdates: Boolean
}

/** Whether this shortcut's feature works on the current platform. */
val Shortcut.isAvailable: Boolean
    get() = when (this) {
        Shortcut.Equalizer -> PlayerCapabilities.equalizer
        Shortcut.BackgroundPlay -> PlayerCapabilities.backgroundPlay
        Shortcut.Rotation -> PlayerCapabilities.screenRotation
        else -> true
    }
