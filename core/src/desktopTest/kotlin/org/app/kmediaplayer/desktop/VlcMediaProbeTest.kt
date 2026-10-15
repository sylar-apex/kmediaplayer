package org.app.kmediaplayer.desktop

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs only with `-PvlcPath=<libvlc dir> -PprobeClips=<folder with the format test clips>`. */
class VlcMediaProbeTest {
    private val clips = System.getProperty("probe.clips")

    @Test fun readsLengthResolutionAndFrameRate() = runBlocking {
        if (clips == null || !VlcMediaProbe.isAvailable) return@runBlocking
        val hd = VlcMediaProbe.probe("$clips/23_1080p60_h264.mkv")!!
        assertEquals(1920 to 1080, hd.width to hd.height)
        assertTrue(hd.durationMs in 14_000..16_000, "duration ${hd.durationMs}")
        assertEquals(60f, hd.frameRate, 0.5f)
        val uhd = VlcMediaProbe.probe("$clips/22_4k60_hevc10_eac3.mkv")!!
        assertEquals(3840 to 2160, uhd.width to uhd.height)
        val wmv = VlcMediaProbe.probe("$clips/01_wmv2_wma.wmv")!!
        assertTrue(wmv.durationMs > 10_000 && wmv.width == 1280)
    }
}
