package org.app.kmediaplayer.android.vlc

import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.AudioEffectsController
import org.app.kmediaplayer.settings.PlayerKeys
import org.videolan.libvlc.MediaPlayer
import kotlin.math.roundToInt

/**
 * Equalizer and volume boost for items played by libVLC. Levels are exchanged in millibels like the
 * Android effects, and converted to libVLC's decibels. libVLC has no bass boost or reverb.
 */
internal class VlcAudioEffects(private val playback: VlcPlayback) : AudioEffectsController {
    private val eq: MediaPlayer.Equalizer = MediaPlayer.Equalizer.create()

    override val presetCount: Int get() = MediaPlayer.Equalizer.getPresetCount()
    override fun presetName(index: Int): String = MediaPlayer.Equalizer.getPresetName(index)

    override fun usePreset(index: Int) {
        val preset = MediaPlayer.Equalizer.createFromPreset(index)
        for (b in 0 until bandCount) eq.setAmp(b, preset.getAmp(b))
        eq.setPreAmp(preset.preAmp)
        push()
    }

    override val bandCount: Int get() = MediaPlayer.Equalizer.getBandCount()
    override val bandLevelRange: IntRange get() = -1500..1500
    override fun bandLevel(band: Int): Int = (eq.getAmp(band) * 100).roundToInt()
    override fun setBandLevel(band: Int, level: Int) { eq.setAmp(band, level / 100f); push() }
    override fun centerFreqHz(band: Int): Int = MediaPlayer.Equalizer.getBandFrequency(band).roundToInt()
    override val hasReverb: Boolean get() = false
    override val hasBassBoost: Boolean get() = false

    override fun applySaved() {
        val s = KMediaPlayer.settings
        val preset = s[PlayerKeys.equalizerPreset]
        if (preset in 0 until presetCount) usePreset(preset)
        else s[PlayerKeys.equalizerBands].split(',').mapNotNull { it.toIntOrNull() }
            .forEachIndexed { i, level -> if (i < bandCount) eq.setAmp(i, level / 100f) }
        push()
    }

    /** Boost maps onto libVLC's software gain: +600 mB is roughly double loudness, capped at 200%. */
    override fun setBoostMillibels(mb: Int) {
        playback.boost = Math.pow(10.0, mb / 2000.0).toFloat()
    }

    private fun push() {
        playback.setEqualizer(if (KMediaPlayer.settings[PlayerKeys.equalizerEnabled]) eq else null)
    }
}
