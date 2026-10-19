package org.app.kmediaplayer.ui.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.app.kmediaplayer.KMediaPlayer
import org.app.kmediaplayer.engine.Decoder
import org.app.kmediaplayer.engine.EngineError
import org.app.kmediaplayer.engine.EngineListener
import org.app.kmediaplayer.engine.EngineState
import org.app.kmediaplayer.engine.MediaEngine
import org.app.kmediaplayer.engine.RepeatMode
import org.app.kmediaplayer.engine.TrackInfo
import org.app.kmediaplayer.engine.TrackType
import org.app.kmediaplayer.logic.AbRepeat
import org.app.kmediaplayer.logic.AspectRatio
import org.app.kmediaplayer.logic.LoopMode
import org.app.kmediaplayer.logic.ResumeDecision
import org.app.kmediaplayer.logic.ResumePolicy
import org.app.kmediaplayer.logic.SmartPrevious
import org.app.kmediaplayer.logic.Speed
import org.app.kmediaplayer.logic.SubtitleDocument
import org.app.kmediaplayer.logic.SubtitleParser
import org.app.kmediaplayer.logic.VolumeState
import org.app.kmediaplayer.logic.ZoomMode
import org.app.kmediaplayer.model.FileSelections
import org.app.kmediaplayer.model.MediaFile
import org.app.kmediaplayer.platform.PlatformMedia
import org.app.kmediaplayer.settings.PlayerKeys
import org.app.kmediaplayer.util.nowMs
import org.jetbrains.compose.resources.DrawableResource

enum class Panel { None, Overflow, Audio, Subtitle, Decoder, Speed, Equalizer, SleepTimer, AbRepeat, Aspect, Info, Bookmark, More, Display, Playlist, Customise }

/** Everything the video screen shows and does, driven by one [MediaEngine]. */
class VideoPlayerState(val engine: MediaEngine, val scope: CoroutineScope) {
    private val settings = KMediaPlayer.settings
    private val host = KMediaPlayer.host

    var queue by mutableStateOf<List<MediaFile>>(emptyList())
    var index by mutableIntStateOf(0)
    var title by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var videoW by mutableIntStateOf(0)
    var videoH by mutableIntStateOf(0)

    var controlsVisible by mutableStateOf(settings[PlayerKeys.showInterfaceAtStartup])
    var shortcutsExpanded by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var panel by mutableStateOf(Panel.None)
    var resumePrompt by mutableStateOf<Long?>(null)
    var resumeNotice by mutableStateOf<Long?>(null)
    var error by mutableStateOf<EngineError?>(null)
    var toast by mutableStateOf<String?>(null)
    var snack by mutableStateOf<String?>(null)
    var gestureLabel by mutableStateOf<String?>(null)
    var levelOsd by mutableStateOf<LevelOsd?>(null)
    var seekOsd by mutableStateOf<Pair<Long, Long>?>(null)
    var tapSeekOsd by mutableStateOf<Pair<Int, Int>?>(null)
    var pillOsd by mutableStateOf<Pair<DrawableResource, String>?>(null)
    var lockTapCount by mutableStateOf(0)

    var zoom by mutableStateOf(ZoomMode.Fit)
    var aspect by mutableStateOf(AspectRatio.Default)
    var customAspect by mutableFloatStateOf(1.85f)
    var pinchScale by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var nightMode by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var loop by mutableStateOf(LoopMode.Off)
    var shuffle by mutableStateOf(false)
    var speed by mutableFloatStateOf(settings[PlayerKeys.defaultSpeed] / 100f)
    var tempSpeed by mutableStateOf<Float?>(null)
    var ab by mutableStateOf(AbRepeat())
    var sleepEndAt by mutableStateOf<Long?>(null)
    var sleepFinishLast by mutableStateOf(false)
    var decoder by mutableStateOf(Decoder.of(settings[PlayerKeys.defaultDecoder]))
    var backgroundPlay by mutableStateOf(settings[PlayerKeys.backgroundMode] == "background")
    var landscape by mutableStateOf<Boolean?>(null)

    var volume by mutableStateOf(VolumeState(PlatformMedia.systemVolume(), PlatformMedia.systemMaxVolume(), settings[PlayerKeys.volumeBoost]))
    var brightness by mutableIntStateOf(settings[PlayerKeys.brightnessLevel])

    var audioTracks by mutableStateOf<List<TrackInfo>>(emptyList())
    var textTracks by mutableStateOf<List<TrackInfo>>(emptyList())
    var subtitlesDisabled by mutableStateOf(false)
    var embeddedCues by mutableStateOf<List<String>>(emptyList())
    var externalSubs by mutableStateOf<SubtitleDocument?>(null)
    var externalSubsName by mutableStateOf<String?>(null)
    var subtitleSyncMs by mutableLongStateOf((settings[PlayerKeys.subtitleDefaultSync] * 1000).toLong())
    var subtitleSpeed by mutableFloatStateOf(1f)
    var subtitleOffsetY by mutableFloatStateOf(0f)
    var subtitleZoom by mutableFloatStateOf(1f)

    var finishRequested by mutableStateOf(false)

    val current: MediaFile? get() = queue.getOrNull(index)
    val effectiveSpeed: Float get() = tempSpeed ?: speed

    private var firstIndex = 0
    private var resumeCheckedFor: String? = null
    private var ticker: Job? = null
    private var hideJob: Job? = null
    private var fadeJob: Job? = null
    private var restoredTracksFor: String? = null

    private val listener = object : EngineListener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; scheduleHide() }
        override fun onStateChanged(state: EngineState) {
            buffering = state == EngineState.Buffering
            duration = engine.duration
            if (state == EngineState.Ready) checkResume()
            if (state == EngineState.Ended) onEnded()
        }
        override fun onItemTransition() {
            saveProgress()
            index = engine.currentIndex
            onItemChanged()
        }
        override fun onVideoSizeChanged(width: Int, height: Int) { videoW = width; videoH = height }
        override fun onTracksChanged() { readTracks(); restoreTracks() }
        override fun onCues(lines: List<String>) { embeddedCues = lines }
        override fun onError(error: EngineError) {
            if (settings[PlayerKeys.suppressError] && engine.hasNext()) { engine.seekToNext(); engine.prepare(); engine.play() }
            else this@VideoPlayerState.error = error
        }
    }

    fun start(files: List<MediaFile>, startIndex: Int) {
        queue = files
        KMediaPlayer.queue.value = files
        KMediaPlayer.isMusicSession.value = false
        engine.decoder = decoder
        engine.setPreferredLanguages(settings[PlayerKeys.audioLanguage], settings[PlayerKeys.subtitleLanguage])
        engine.setItems(files, startIndex, 0)
        index = startIndex
        firstIndex = startIndex
        attach()
        onItemChanged()
        engine.prepare()
        engine.play()
    }

    fun startUrl(url: String) {
        val display = (PlatformMedia.displayName(url) ?: url).substringBeforeLast('.').ifEmpty { url }
        val stored = if (url.startsWith("content")) PlatformMedia.lookup(url) else null
        queue = listOf(stored ?: MediaFile(url.hashCode().toLong(), url, url, display, 0, 0, 0))
        KMediaPlayer.queue.value = emptyList()
        KMediaPlayer.isMusicSession.value = false
        engine.setStream(url)
        index = 0
        attach()
        title = queue[0].title
        engine.prepare()
        engine.play()
    }

    /** Re-attaches to a player already running in the background. */
    fun reattach() {
        queue = KMediaPlayer.queue.value
        index = engine.currentIndex
        attach()
        isPlaying = engine.isPlaying
        duration = engine.duration
        title = engine.title ?: ""
        readTracks()
    }

    private fun attach() {
        engine.removeListener(listener)
        engine.addListener(listener)
        engine.speed = speed
        engine.repeatMode = RepeatMode.Off
        ticker?.cancel()
        ticker = scope.launch {
            var n = 0
            while (isActive) {
                position = engine.position
                ab.loopTarget(position)?.let { engine.seekTo(it) }
                sleepEndAt?.let { end ->
                    if (nowMs() >= end) {
                        sleepEndAt = null
                        if (sleepFinishLast) engine.pauseAtEndOfItems = true else engine.pause()
                    }
                }
                if (++n % 10 == 0) saveProgress()
                delay(200)
            }
        }
    }

    fun detach() {
        saveProgress()
        ticker?.cancel()
        engine.removeListener(listener)
    }

    private fun onItemChanged() {
        val f = current ?: return
        title = f.title
        ab = AbRepeat()
        externalSubs = null; externalSubsName = null
        subtitleSyncMs = (settings[PlayerKeys.subtitleDefaultSync] * 1000).toLong()
        resumeCheckedFor = null
        restoredTracksFor = null
        if (settings[PlayerKeys.rememberSelections]) {
            host.selections(f.uri)?.let { s ->
                s.zoom?.let { z -> ZoomMode.entries.firstOrNull { it.name == z }?.let { zoom = it } }
                s.aspect?.let { a -> AspectRatio.entries.firstOrNull { it.name == a }?.let { aspect = it } }
                s.speed?.let { setSpeed(it, remember = false) }
                s.subtitleSyncMs?.let { subtitleSyncMs = it }
                s.decoder?.let { decoder = Decoder.of(it) }
            }
        }
        autoLoadSubtitle(f)
        fadeIn(settings[PlayerKeys.fadeOnStart])
    }

    private fun checkResume() {
        val f = current ?: return
        if (resumeCheckedFor == f.uri) return
        resumeCheckedFor = f.uri
        val rec = host.playbackRecord(f.uri) ?: return
        when (ResumePolicy.decide(settings[PlayerKeys.resume], rec.positionMs, engine.duration, index == firstIndex, settings[PlayerKeys.resumeOnlyFirst])) {
            ResumeDecision.Resume -> { engine.seekTo(rec.positionMs); resumeNotice = rec.positionMs }
            ResumeDecision.Ask -> { engine.pause(); resumePrompt = rec.positionMs }
            ResumeDecision.StartOver -> {}
        }
    }

    fun startOverFromNotice() { resumeNotice = null; engine.seekTo(0); scheduleHide() }

    fun answerResume(resume: Boolean) {
        resumePrompt?.let { if (resume) engine.seekTo(it) }
        resumePrompt = null
        engine.play()
    }

    private fun onEnded() {
        saveProgress()
        when {
            loop == LoopMode.One -> { engine.seekTo(0); engine.play() }
            loop == LoopMode.All && queue.isNotEmpty() -> { engine.seekTo(0, 0); engine.play() }
            settings[PlayerKeys.backToList] -> finishRequested = true
        }
    }

    fun saveProgress() {
        val f = current ?: return
        if (resumeCheckedFor != f.uri) return
        if (f.uri.startsWith("http") || f.uri.startsWith("rtsp")) return
        val d = engine.duration
        if (d <= 0) return
        host.recordPlayback(f, engine.position, d)
    }

    private fun rememberSelection(transform: (FileSelections) -> FileSelections) {
        val f = current ?: return
        if (!settings[PlayerKeys.rememberSelections]) return
        host.updateSelections(f.uri, transform)
    }

    // ---- transport ----

    fun togglePlay() {
        if (engine.isPlaying) engine.pause() else { if (engine.state == EngineState.Ended) engine.seekTo(0); engine.play() }
        scheduleHide()
    }

    fun seekBy(deltaMs: Long) { seekTo(engine.position + deltaMs) }

    fun seekTo(ms: Long) {
        engine.seekTo(ms.coerceIn(0, engine.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        position = engine.position
        fadeIn(settings[PlayerKeys.fadeOnSeek])
    }

    fun next() {
        if (settings[PlayerKeys.prevNextToRewFf]) { seekBy(settings[PlayerKeys.moveInterval] * 1000L); return }
        if (shuffle && queue.size > 1) engine.seekTo(pickShuffled(), 0)
        else if (engine.hasNext()) engine.seekToNext()
        else toast = "Last item"
    }

    fun previous() {
        if (settings[PlayerKeys.prevNextToRewFf]) { seekBy(-settings[PlayerKeys.moveInterval] * 1000L); return }
        if (SmartPrevious.shouldRestart(settings[PlayerKeys.smartPrev], engine.position) || !engine.hasPrevious()) engine.seekTo(0)
        else engine.seekToPrevious()
    }

    fun jumpTo(i: Int) { if (i in queue.indices) { engine.seekTo(i, 0); engine.play() } }

    private fun pickShuffled(): Int = queue.indices.filter { it != index }.random()

    fun setSpeed(v: Float, remember: Boolean = true) {
        speed = Speed.clamp(v)
        if (tempSpeed == null) engine.speed = speed
        if (remember) rememberSelection { it.copy(speed = speed) }
    }

    fun setTemporarySpeed(v: Float?) { tempSpeed = v; engine.speed = v ?: speed }

    private var zoomLabelJob: Job? = null

    /**
     * Scales the picture by [factor] the way a two-finger pinch does; desktops call it for the mouse wheel and
     * trackpad pinch, which never reach the touch gesture code.
     */
    fun zoomBy(factor: Float) {
        if (locked || !(settings[PlayerKeys.gZoom] || settings[PlayerKeys.gZoomAndPan])) return
        pinchScale = (pinchScale * factor).coerceIn(0.25f, 4f)
        gestureLabel = "${(pinchScale * 100).toInt()}%"
        zoomLabelJob?.cancel()
        zoomLabelJob = scope.launch { delay(600); gestureLabel = null }
    }

    fun cycleZoom() {
        zoom = zoom.next(); pinchScale = 1f; panX = 0f; panY = 0f
        toast = zoom.label
        rememberSelection { it.copy(zoom = zoom.name) }
    }

    fun setAspectRatio(a: AspectRatio, applyToAll: Boolean) {
        aspect = a
        if (applyToAll && a != AspectRatio.Custom) settings[PlayerKeys.defaultAspect] = a.name
        rememberSelection { it.copy(aspect = a.name) }
    }

    fun aspectValue(): Float? = when (aspect) {
        AspectRatio.Custom -> customAspect
        AspectRatio.Default -> AspectRatio.valueOf(settings[PlayerKeys.defaultAspect]).ratio
        else -> aspect.ratio
    }

    fun toggleMute() { muted = !muted; engine.volume = if (muted) 0f else 1f; toast = if (muted) "Mute" else "Unmute" }

    fun cycleLoop() { loop = loop.next(); toast = loop.label }

    fun toggleShuffle() { shuffle = !shuffle; toast = if (shuffle) "Shuffle on" else "Shuffle off" }

    fun setDecoderMode(d: Decoder) {
        if (d == decoder) return
        decoder = d
        engine.decoder = d
        val pos = engine.position
        val i = engine.currentIndex
        engine.stop()
        engine.seekTo(i, pos)
        engine.prepare(); engine.play()
        rememberSelection { it.copy(decoder = d.key) }
        toast = "${d.label} decoder"
    }

    fun startSleepTimer(minutes: Int, finishLast: Boolean) {
        sleepEndAt = nowMs() + minutes * 60_000L
        sleepFinishLast = finishLast
        engine.pauseAtEndOfItems = false
        toast = "Sleep timer: ${minutes / 60}h ${minutes % 60}m"
    }

    fun stopSleepTimer() { sleepEndAt = null; engine.pauseAtEndOfItems = false }

    // ---- volume ----

    fun adjustVolume(delta: Int) {
        volume = volume.copy(level = PlatformMedia.systemVolume().let { sys -> if (volume.level > volume.systemMax) volume.level else sys }).adjust(delta)
        PlatformMedia.setSystemVolume(volume.systemLevel)
        engine.effects?.setBoostMillibels(volume.boostMillibels)
        if (muted && delta > 0) toggleMute()
    }

    // ---- tracks ----

    private fun readTracks() {
        audioTracks = engine.tracks(TrackType.Audio)
        textTracks = engine.tracks(TrackType.Text)
        subtitlesDisabled = engine.isTrackTypeDisabled(TrackType.Text)
    }

    private fun restoreTracks() {
        val f = current ?: return
        if (restoredTracksFor == f.uri || audioTracks.isEmpty() && textTracks.isEmpty()) return
        restoredTracksFor = f.uri
        if (!settings[PlayerKeys.rememberSelections]) return
        val s = host.selections(f.uri) ?: return
        s.audioTrack?.let { id -> audioTracks.firstOrNull { it.id == id }?.let { selectTrack(it, TrackType.Audio, remember = false) } }
        s.subtitleTrack?.let { id -> if (id == "off") disableSubtitles(remember = false) else textTracks.firstOrNull { it.id == id }?.let { selectTrack(it, TrackType.Text, remember = false) } }
    }

    fun selectTrack(t: TrackInfo, type: TrackType, remember: Boolean = true) {
        engine.selectTrack(type, t.id)
        if (type == TrackType.Text) subtitlesDisabled = false
        if (remember) rememberSelection { if (type == TrackType.Audio) it.copy(audioTrack = t.id) else it.copy(subtitleTrack = t.id) }
    }

    fun disableAudio() = engine.setTrackTypeDisabled(TrackType.Audio, true)

    fun disableSubtitles(remember: Boolean = true) {
        engine.setTrackTypeDisabled(TrackType.Text, true)
        subtitlesDisabled = true
        externalSubs = null; externalSubsName = null
        if (remember) rememberSelection { it.copy(subtitleTrack = "off") }
    }

    /** Shows or hides subtitles without forgetting a loaded subtitle file (keyboard "C"). */
    fun toggleSubtitles() {
        subtitlesDisabled = !subtitlesDisabled
        if (externalSubs == null) engine.setTrackTypeDisabled(TrackType.Text, subtitlesDisabled)
        toast = if (subtitlesDisabled) "Subtitles off" else "Subtitles on"
    }

    // ---- external subtitles ----

    fun loadSubtitle(uri: String, name: String) {
        val ext = name.substringAfterLast('.', "srt").lowercase()
        if (ext !in SubtitleParser.supportedExtensions) { toast = "Unsupported subtitle format: .$ext"; return }
        val text = PlatformMedia.readBytes(uri)?.let(::decodeSubtitle) ?: run { toast = "Can't read subtitle"; return }
        externalSubs = SubtitleParser.parse(text, ext, settings[PlayerKeys.italicTag])
        externalSubsName = name
        engine.setTrackTypeDisabled(TrackType.Text, true)
        subtitlesDisabled = false
        toast = "Subtitle: $name"
    }

    private fun decodeSubtitle(bytes: ByteArray): String? = runCatching {
        val forced = settings[PlayerKeys.subtitleCharset]
        if (forced.isNotEmpty()) return@runCatching PlatformMedia.decodeText(bytes, forced)
        val utf8 = bytes.decodeToString()
        if ('�' in utf8) PlatformMedia.decodeText(bytes, "ISO-8859-1") else utf8
    }.getOrNull()

    private fun autoLoadSubtitle(f: MediaFile) {
        runCatching { host.subtitleFiles(f) }.getOrDefault(emptyList())
            .firstOrNull { it.name.substringAfterLast('.').lowercase() in SubtitleParser.supportedExtensions }
            ?.let { loadSubtitle(it.uri, it.name) }
    }

    fun setSubtitleSync(ms: Long) { subtitleSyncMs = ms; rememberSelection { it.copy(subtitleSyncMs = ms) } }

    fun visibleSubtitleLines(): List<SubLine> {
        if (subtitlesDisabled) return emptyList()
        val ext = externalSubs
        return if (ext != null) ext.cuesAt(position, subtitleSyncMs, subtitleSpeed).map { SubLine(it.text, it.italic) }
        else embeddedCues.map { SubLine(it, false) }
    }

    // ---- interface visibility ----

    fun showControls() { controlsVisible = true; scheduleHide() }

    fun toggleControls() { controlsVisible = !controlsVisible; if (controlsVisible) scheduleHide() }

    fun scheduleHide() {
        hideJob?.cancel()
        if (!settings[PlayerKeys.interfaceAutoHide] || !engine.isPlaying) return
        hideJob = scope.launch {
            delay((settings[PlayerKeys.interfaceAutoHideDelay] * 1000).toLong())
            if (panel == Panel.None) controlsVisible = false
        }
    }

    private fun fadeIn(enabled: Boolean) {
        if (!enabled || muted) return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            for (i in 0..10) { engine.volume = i / 10f; delay(40) }
        }
    }
}

data class SubLine(val text: String, val italic: Boolean)

/** [boostFrom] is the bar fraction where volume boost starts, or null when there is no boost range. */
data class LevelOsd(val brightness: Boolean, val value: Int, val fraction: Float, val boostFrom: Float?)
