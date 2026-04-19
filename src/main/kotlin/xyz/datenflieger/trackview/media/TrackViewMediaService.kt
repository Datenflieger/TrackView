package xyz.datenflieger.trackview.media

import org.endlesssource.mediainterface.SystemMediaFactory
import org.endlesssource.mediainterface.api.PlaybackState
import org.endlesssource.mediainterface.api.SystemMediaInterface
import org.endlesssource.mediainterface.api.SystemMediaOptions
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object TrackViewMediaService {
    private val logger = LoggerFactory.getLogger("TrackView / Media")

    private val snapshotRef = AtomicReference(TrackViewSnapshot.empty())

    @Volatile
    private var scheduler: ScheduledExecutorService? = null

    @Volatile
    private var mediaInterface: SystemMediaInterface? = null

    @Volatile
    private var started = false

    @Volatile
    private var unsupportedLogged = false

    @Volatile
    private var lastNonEmptySnapshot: TrackViewSnapshot? = null

    private const val POLL_INTERVAL_MS = 250L
    private const val STALE_GRACE_MS = 1_500L

    private val mediaOptions: SystemMediaOptions = SystemMediaOptions.defaults()
        .withEventDrivenEnabled(true)
        .withPositionUpdatesEnabled(true)
        .withSessionPollInterval(Duration.ofMillis(POLL_INTERVAL_MS))
        .withSessionUpdateInterval(Duration.ofMillis(POLL_INTERVAL_MS))

    fun start() {
        synchronized(this) {
            if (started) {
                return
            }

            started = true
            scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "TrackView-MediaPoll").apply { isDaemon = true }
            }

            scheduler?.scheduleWithFixedDelay(::poll, 0L, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
    }

    fun stop() {
        synchronized(this) {
            started = false
            scheduler?.shutdownNow()
            scheduler = null

            mediaInterface?.close()
            mediaInterface = null

            TrackViewArtworkCache.clear()
            snapshotRef.set(TrackViewSnapshot.empty())
            lastNonEmptySnapshot = null
            unsupportedLogged = false
        }
    }

    fun snapshot(): TrackViewSnapshot {
        return snapshotRef.get()
    }

    private fun poll() {
        val media = getOrCreateInterface() ?: return

        try {
            val session = media.activeSession.orElse(null)
            if (session == null) {
                publishNoMedia(PlaybackState.UNKNOWN)
                return
            }

            val playbackState = try {
                session.controls.playbackState
            } catch (_: Throwable) {
                PlaybackState.UNKNOWN
            }

            val nowPlaying = session.nowPlaying.orElse(null)
            val title = nowPlaying?.title?.orElse("").orEmpty().trim()
            val artist = nowPlaying?.artist?.orElse("").orEmpty().trim()
            val source = session.applicationName.orEmpty().trim()
            val duration = nowPlaying?.duration?.map(Duration::toMillis)?.orElse(null)
            val position = nowPlaying?.position?.map(Duration::toMillis)?.orElse(null)
            val artwork = nowPlaying?.artwork?.orElse(null)?.trim().takeUnless { it.isNullOrEmpty() }

            val hasSignal = title.isNotBlank() || artist.isNotBlank() || source.isNotBlank() || playbackState != PlaybackState.UNKNOWN
            if (!hasSignal) {
                publishNoMedia(playbackState)
                return
            }

            val snapshot = TrackViewSnapshot(
                hasMedia = true,
                title = title,
                artist = artist,
                sourceApp = source,
                playbackState = playbackState,
                durationMillis = duration,
                positionMillis = position,
                artworkToken = artwork,
                updatedAt = System.currentTimeMillis()
            )

            snapshotRef.set(snapshot)
            lastNonEmptySnapshot = snapshot
        } catch (t: Throwable) {
            logger.debug("Media poll failed", t)
            publishNoMedia(PlaybackState.UNKNOWN)
        }
    }

    private fun publishNoMedia(state: PlaybackState) {
        val now = System.currentTimeMillis()
        val stale = lastNonEmptySnapshot
        if (stale != null && now - stale.updatedAt <= STALE_GRACE_MS) {
            snapshotRef.set(stale.copy(playbackState = state, updatedAt = now))
            return
        }

        snapshotRef.set(TrackViewSnapshot.empty().copy(playbackState = state, updatedAt = now))
    }

    private fun getOrCreateInterface(): SystemMediaInterface? {
        mediaInterface?.let { return it }

        synchronized(this) {
            mediaInterface?.let { return it }

            return try {
                val created = SystemMediaFactory.createSystemInterface(mediaOptions)
                mediaInterface = created
                unsupportedLogged = false
                created
            } catch (t: Throwable) {
                if (!unsupportedLogged) {
                    unsupportedLogged = true
                    logger.warn("Media interface unavailable: {}", t.message)
                }
                null
            }
        }
    }
}
