package org.app.kmediaplayer.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FallbackBackendTest {
    private val primary = FakeBackend("primary")
    private val secondary = FakeBackend("secondary")
    private val errors = mutableListOf<String>()
    private val backend = FallbackBackend(primary, { secondary }) { it.endsWith(".mp4") }.apply {
        callback = object : BackendCallback {
            override fun onPlayingChanged(playing: Boolean) {}
            override fun onStateChanged(state: EngineState) {}
            override fun onVideoSize(width: Int, height: Int) {}
            override fun onTracksChanged() {}
            override fun onEnded() {}
            override fun onError(codeName: String) { errors += codeName }
        }
    }

    @Test fun nativeFormatsGoToThePrimary() {
        backend.open("a.mp4", 0, true, Decoder.HW)
        assertSame(primary, backend.active)
        assertEquals(listOf("a.mp4"), primary.opened)
    }

    @Test fun otherFormatsGoToTheSecondary() {
        backend.open("a.wmv", 0, true, Decoder.HW)
        assertSame(secondary, backend.active)
    }

    @Test fun softwareDecoderAlwaysUsesTheSecondary() {
        backend.open("a.mp4", 0, true, Decoder.SW)
        assertSame(secondary, backend.active)
    }

    @Test fun aPrimaryFailureReopensInTheSecondaryAtTheSamePosition() {
        backend.open("a.mp4", 0, true, Decoder.HW)
        primary.position = 12_000
        primary.fail()
        assertSame(secondary, backend.active)
        assertEquals(listOf("a.mp4"), secondary.opened)
        assertEquals(12_000, secondary.openedAt)
        assertTrue(errors.isEmpty())
    }

    @Test fun aSecondaryFailureIsReported() {
        backend.open("a.wmv", 0, true, Decoder.HW)
        secondary.fail("ERROR_CODE_DECODING_FAILED")
        assertEquals(listOf("ERROR_CODE_DECODING_FAILED"), errors)
    }

    @Test fun withoutASecondaryThePrimaryErrorIsReported() {
        val alone = FallbackBackend(primary, { null }) { true }
        var reported: String? = null
        alone.callback = object : BackendCallback {
            override fun onPlayingChanged(playing: Boolean) {}
            override fun onStateChanged(state: EngineState) {}
            override fun onVideoSize(width: Int, height: Int) {}
            override fun onTracksChanged() {}
            override fun onEnded() {}
            override fun onError(codeName: String) { reported = codeName }
        }
        alone.open("a.mkv", 0, true, Decoder.HW)
        primary.fail()
        assertEquals("ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED", reported)
    }

    @Test fun eventsFromTheInactiveBackendAreIgnored() {
        backend.open("a.wmv", 0, true, Decoder.HW)
        primary.fail()
        assertTrue(errors.isEmpty())
    }
}
