package org.app.kmediaplayer.web

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.files.Blob
import kotlin.js.Promise

/**
 * Bytes of small files the user picked (subtitles), keyed by their blob URL. Browsers read files
 * asynchronously while the subtitle parser reads synchronously, so files are read once up front.
 */
object WebBlobStore {
    private val bytes = mutableMapOf<String, ByteArray>()

    fun put(url: String, data: ByteArray) { bytes[url] = data }
    fun get(url: String): ByteArray? = bytes[url]

    suspend fun readAll(blob: Blob): ByteArray {
        val view = Int8Array(arrayBuffer(blob).await<ArrayBuffer>())
        return ByteArray(view.length) { view[it] }
    }
}

@JsFun("(b) => b.arrayBuffer()")
private external fun arrayBuffer(blob: Blob): Promise<ArrayBuffer>
