package org.app.kmediaplayer.engine

import org.app.kmediaplayer.model.MediaFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueuedMediaEngineTest {
    private val backend = FakeBackend()
    private val engine = QueuedMediaEngine(backend) { it() }
    private val files = (1..3).map { MediaFile(it.toLong(), "file:///v$it.mkv", "/v$it.mkv", "v$it.mkv", 0, 0, 0) }

    private fun start(index: Int = 0, positionMs: Long = 0) {
        engine.setItems(files, index, positionMs)
        engine.play()
        engine.prepare()
    }

    @Test fun preparesTheStartItemAtTheStartPosition() {
        start(index = 1, positionMs = 4_000)
        assertEquals(listOf("file:///v2.mkv"), backend.opened)
        assertEquals(4_000, backend.openedAt)
        assertTrue(backend.playing)
    }

    @Test fun advancesToTheNextItemWhenOneEnds() {
        start()
        backend.end()
        assertEquals(1, engine.currentIndex)
        assertEquals("file:///v2.mkv", backend.opened.last())
    }

    @Test fun stopsAtTheEndOfTheQueue() {
        start(index = 2)
        backend.end()
        assertEquals(EngineState.Ended, engine.state)
        assertEquals(1, backend.opened.size)
    }

    @Test fun repeatAllWrapsToTheFirstItem() {
        engine.repeatMode = RepeatMode.All
        start(index = 2)
        backend.end()
        assertEquals(0, engine.currentIndex)
    }

    @Test fun repeatOneReplaysTheSameItem() {
        engine.repeatMode = RepeatMode.One
        start(index = 1)
        backend.position = 9_000
        backend.end()
        assertEquals(1, engine.currentIndex)
        assertEquals(0, backend.position)
        assertEquals(1, backend.opened.size)
    }

    @Test fun pauseAtEndOfItemsOpensTheNextItemPaused() {
        engine.pauseAtEndOfItems = true
        start()
        backend.end()
        assertEquals(1, engine.currentIndex)
        assertFalse(backend.playing)
    }

    @Test fun nextAndPreviousMoveThroughTheQueue() {
        start()
        engine.seekToNext()
        engine.seekToNext()
        assertEquals(2, engine.currentIndex)
        assertFalse(engine.hasNext())
        engine.seekToPrevious()
        assertEquals(1, engine.currentIndex)
    }

    @Test fun shuffleKeepsTheCurrentItemAndVisitsEveryItemOnce() {
        start(index = 1)
        engine.shuffleEnabled = true
        assertEquals(1, engine.currentIndex)
        val seen = mutableSetOf(engine.currentIndex)
        while (engine.hasNext()) { engine.seekToNext(); seen += engine.currentIndex }
        assertEquals(setOf(0, 1, 2), seen)
    }

    @Test fun softwareDecoderChoiceReachesTheBackend() {
        engine.decoder = Decoder.SW
        start()
        assertEquals(Decoder.SW, backend.lastDecoder)
    }

    @Test fun eventsReachListeners() {
        var transitions = 0
        engine.addListener(object : EngineListener { override fun onItemTransition() { transitions++ } })
        start()
        backend.end()
        assertEquals(2, transitions)
    }
}
