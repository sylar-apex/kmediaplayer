package org.app.kmediaplayer.android.exo

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.AudioEffectsController
import org.app.kmediaplayer.settings.PlayerKeys

/** Equalizer, bass boost, reverb and loudness (volume boost) bound to one audio session. */
internal class AndroidAudioEffects(sessionId: Int) : AudioEffectsController {
    val equalizer = Equalizer(0, sessionId)
    val bassBoost: BassBoost? = runCatching { BassBoost(0, sessionId) }.getOrNull()
    val reverb: PresetReverb? = runCatching { PresetReverb(0, sessionId) }.getOrNull()
    val loudness: LoudnessEnhancer? = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()

    init { applySaved() }

    override val presetCount: Int get() = equalizer.numberOfPresets.toInt()
    override fun presetName(index: Int): String = equalizer.getPresetName(index.toShort())
    override fun usePreset(index: Int) { runCatching { equalizer.usePreset(index.toShort()) } }
    override val bandCount: Int get() = equalizer.numberOfBands.toInt()
    override val bandLevelRange: IntRange get() = equalizer.bandLevelRange.let { it[0].toInt()..it[1].toInt() }
    override fun bandLevel(band: Int): Int = equalizer.getBandLevel(band.toShort()).toInt()
    override fun setBandLevel(band: Int, level: Int) { runCatching { equalizer.setBandLevel(band.toShort(), level.toShort()) } }
    override fun centerFreqHz(band: Int): Int = equalizer.getCenterFreq(band.toShort()) / 1000
    override val hasReverb: Boolean get() = reverb != null
    override val hasBassBoost: Boolean get() = bassBoost != null

    override fun applySaved() {
        val s = KMediaPlayer.settings
        val on = s[PlayerKeys.equalizerEnabled]
        runCatching {
            equalizer.enabled = on
            val preset = s[PlayerKeys.equalizerPreset]
            val bands = s[PlayerKeys.equalizerBands].split(',').mapNotNull { it.toShortOrNull() }
            if (preset >= 0 && preset < equalizer.numberOfPresets) equalizer.usePreset(preset.toShort())
            else bands.forEachIndexed { i, level -> if (i < equalizer.numberOfBands) equalizer.setBandLevel(i.toShort(), level) }
            bassBoost?.apply { enabled = on && s[PlayerKeys.bassBoost] > 0; if (strengthSupported) setStrength(s[PlayerKeys.bassBoost].toShort()) }
            reverb?.apply { setPreset(s[PlayerKeys.reverb].toShort()); enabled = on && s[PlayerKeys.reverb] > 0 }
        }
    }

    override fun setBoostMillibels(mb: Int) {
        loudness?.runCatching { setTargetGain(mb); enabled = mb > 0 }
    }

    fun release() {
        runCatching { equalizer.release() }
        runCatching { bassBoost?.release() }
        runCatching { reverb?.release() }
        runCatching { loudness?.release() }
    }
}
