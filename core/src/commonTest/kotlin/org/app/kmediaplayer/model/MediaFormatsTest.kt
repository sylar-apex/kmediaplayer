package org.app.kmediaplayer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaFormatsTest {
    @Test fun extensionOfPathsAndUrls() {
        assertEquals("mkv", "/sdcard/Movies/Film.Name.MKV".fileExtension())
        assertEquals("m3u8", "https://cdn.example.com/live/index.m3u8?token=a.b".fileExtension())
        assertEquals("mp4", "file:///a/b.mp4#t=10".fileExtension())
        assertEquals("", "content://media/external/video/media/42".fileExtension())
        assertEquals("", "https://example.com/folder.with.dots/stream".fileExtension())
    }

    @Test fun legacyFormatsIncludeTheCommonOldContainers() {
        assertTrue(MediaFormats.legacyExtensions.containsAll(listOf("wmv", "rmvb", "vob", "m2ts", "wma", "ape")))
    }
}
