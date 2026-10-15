package org.app.kmediaplayer.model

/** File types the player recognises, shared by the engines and the app's media library. */
object MediaFormats {
    /**
     * Video containers that system media scanners often do not classify as video, and that the
     * Android ExoPlayer engine cannot demux, so libVLC plays them.
     */
    val legacyVideoExtensions: Set<String> = setOf(
        "wmv", "asf", "rm", "rmvb", "divx", "xvid", "vob", "ifo", "evo", "m2v", "mpv", "dv", "dat",
        "nsv", "nut", "ogm", "mxf", "wtv", "dvr-ms", "bik", "smk", "amv", "mts", "m2ts", "tod",
    )

    /** Audio formats outside the Android ExoPlayer engine's extractors. */
    val legacyAudioExtensions: Set<String> = setOf("wma", "ra", "ram", "tta", "ape", "wv", "mpc", "dts")

    val legacyExtensions: Set<String> = legacyVideoExtensions + legacyAudioExtensions
}

/** Lower-case extension of a file path or URL, ignoring any query string or fragment. */
fun String.fileExtension(): String =
    substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase()
