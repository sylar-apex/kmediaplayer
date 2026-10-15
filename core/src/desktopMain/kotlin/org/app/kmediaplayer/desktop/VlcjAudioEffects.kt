package org.app.kmediaplayer.desktop

import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.AudioEffectsController
import org.app.kmediaplayer.settings.PlayerKeys
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.Equalizer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import kotlin.math.pow
import kotlin.math.roundToInt

/** libVLC's 10-band equalizer and software volume boost. Levels are millibels, as on Android. */
internal class VlcjAudioEffects(
    factory: MediaPlayerFactory,
    private val player: MediaPlayer,
    private val setBoost: (Float) -> Unit,
) : AudioEffectsController {
    private val api = factory.equalizer()
    private val presets: List<String> = api.presets()
    private val bands: List<Float> = api.bands()
    private val eq: Equalizer = api.newEqualizer()

    override val presetCount: Int get() = presets.size
    override fun presetName(index: Int): String = presets[index]
    override fun usePreset(index: Int) { eq.setEqualizer(api.newEqualizer(presets[index])); push() }
    override val bandCount: Int get() = bands.size
    override val bandLevelRange: IntRange get() = -1500..1500
    override fun bandLevel(band: Int): Int = (eq.amp(band) * 100).roundToInt()
    override fun setBandLevel(band: Int, level: Int) { eq.setAmp(band, level / 100f); push() }
    override fun centerFreqHz(band: Int): Int = bands[band].roundToInt()
    override val hasReverb: Boolean get() = false
    override val hasBassBoost: Boolean get() = false

    override fun applySaved() {
        val s = KMediaPlayer.settings
        val preset = s[PlayerKeys.equalizerPreset]
        if (preset in presets.indices) usePreset(preset)
        else s[PlayerKeys.equalizerBands].split(',').mapNotNull { it.toIntOrNull() }
            .forEachIndexed { i, level -> if (i < bandCount) eq.setAmp(i, level / 100f) }
        push()
    }

    override fun setBoostMillibels(mb: Int) = setBoost(10.0.pow(mb / 2000.0).toFloat())

    private fun push() {
        player.audio().setEqualizer(if (KMediaPlayer.settings[PlayerKeys.equalizerEnabled]) eq else null)
    }
}
