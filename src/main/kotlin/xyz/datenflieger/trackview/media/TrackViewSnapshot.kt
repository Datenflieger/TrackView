package xyz.datenflieger.trackview.media

import org.endlesssource.mediainterface.api.PlaybackState

data class TrackViewSnapshot(
    val hasMedia: Boolean,
    val title: String,
    val artist: String,
    val sourceApp: String,
    val playbackState: PlaybackState,
    val durationMillis: Long?,
    val positionMillis: Long?,
    val artworkToken: String?,
    val updatedAt: Long
) {
    companion object {
        fun empty(): TrackViewSnapshot {
            return TrackViewSnapshot(
                hasMedia = false,
                title = "",
                artist = "",
                sourceApp = "",
                playbackState = PlaybackState.UNKNOWN,
                durationMillis = null,
                positionMillis = null,
                artworkToken = null,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
