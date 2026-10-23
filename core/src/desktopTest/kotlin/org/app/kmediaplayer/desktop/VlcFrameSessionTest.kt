package org.app.kmediaplayer.desktop

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real-file check, skipped unless FRAME_SESSION_FILE points at a video: one open, many fast seeks. */
class VlcFrameSessionTest {
    @Test
    fun grabsManyFramesFromOneOpenFile() = runBlocking {
        val path = System.getenv("FRAME_SESSION_FILE")?.takeIf { File(it).exists() } ?: return@runBlocking
        val t0 = System.currentTimeMillis()
        val session = VlcFrameSession.open(path, 240) ?: return@runBlocking
        val opened = System.currentTimeMillis() - t0
        val times = (0 until 20).map { it * 9_000L }
        val per = times.map { ms ->
            val s = System.currentTimeMillis()
            val f = session.frameAt(ms)
            Triple(ms, f != null, System.currentTimeMillis() - s)
        }
        session.close()
        println("open ${opened}ms; frames: " + per.joinToString { "${it.first / 1000}s=${if (it.second) "ok" else "miss"}/${it.third}ms" })
        assertTrue(per.count { it.second } >= 18)
    }
}
