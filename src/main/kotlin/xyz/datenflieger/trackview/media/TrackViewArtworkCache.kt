package xyz.datenflieger.trackview.media

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.endlesssource.mediainterface.api.ArtworkDecoder
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.imageio.ImageIO

object TrackViewArtworkCache {
    private val logger = LoggerFactory.getLogger("TrackView / ArtworkCache")
    private val loader: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TrackView-ArtworkLoader").apply { isDaemon = true }
    }

    private val cache = ConcurrentHashMap<String, Entry>()

    private const val MAX_ENTRIES = 32
    private const val USER_AGENT = "TrackView/1.0"
    private const val RETRY_COOLDOWN_MS = 10_000L

    fun request(artworkToken: String): Identifier? {
        val normalized = artworkToken.trim()
        if (normalized.isEmpty()) {
            return null
        }

        val entry = cache.computeIfAbsent(normalized) { token ->
            val identifier = Identifier.fromNamespaceAndPath("trackview", "hud/artwork/${token.hashCode().toUInt()}")
            val created = Entry(identifier, State.LOADING, nowMs())
            scheduleLoad(token, created)
            created
        }

        if (entry.state == State.FAILED && nowMs() - entry.lastFailure > RETRY_COOLDOWN_MS) {
            entry.state = State.LOADING
            scheduleLoad(normalized, entry)
        }

        entry.lastAccess = nowMs()
        evictIfNeeded()

        return if (entry.state == State.READY) entry.identifier else null
    }

    fun clear() {
        val client = Minecraft.getInstance()
        cache.values.forEach { entry ->
            client.execute {
                client.textureManager.release(entry.identifier)
            }
        }
        cache.clear()
    }

    private fun scheduleLoad(token: String, entry: Entry) {
        loader.execute {
            try {
                val bytes = decodeArtworkBytes(token)
                if (bytes == null || bytes.isEmpty()) {
                    markFailed(entry)
                    logger.debug("No artwork bytes for token prefix: {}", token.take(48))
                    return@execute
                }

                val image = decodeNativeImage(bytes)
                if (image == null) {
                    markFailed(entry)
                    logger.debug("Failed to decode image bytes for token prefix: {}", token.take(48))
                    return@execute
                }

                val client = Minecraft.getInstance()
                client.execute {
                    try {
                        client.textureManager.register(entry.identifier, DynamicTexture(entry.identifier::toString, image))
                        entry.state = State.READY
                    } catch (t: Throwable) {
                        markFailed(entry)
                        client.textureManager.release(entry.identifier)
                        logger.debug("Failed to register artwork texture", t)
                    }
                }
            } catch (t: Throwable) {
                markFailed(entry)
                logger.debug("Failed to decode artwork", t)
            }
        }
    }

    private fun markFailed(entry: Entry) {
        entry.state = State.FAILED
        entry.lastFailure = nowMs()
    }

    private fun nowMs(): Long {
        return System.currentTimeMillis()
    }

    private fun decodeNativeImage(bytes: ByteArray): NativeImage? {
        try {
            return NativeImage.read(ByteArrayInputStream(bytes))
        } catch (_: Throwable) {
        }

        return try {
            val buffered = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
            toNativeImage(buffered)
        } catch (_: Throwable) {
            null
        }
    }

    private fun toNativeImage(bufferedImage: BufferedImage): NativeImage {
        val width = bufferedImage.width
        val height = bufferedImage.height
        val nativeImage = NativeImage(NativeImage.Format.RGBA, width, height, true)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val argb = bufferedImage.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val abgr = (a shl 24) or (b shl 16) or (g shl 8) or r
                nativeImage.setPixelABGR(x, y, abgr)
            }
        }

        return nativeImage
    }

    private fun decodeArtworkBytes(token: String): ByteArray? {
        return when {
            token.startsWith("http://", ignoreCase = true) || token.startsWith("https://", ignoreCase = true) -> fetchHttpBytes(token)
            else -> ArtworkDecoder.decodeBytes(token).orElse(null)
        }
    }

    private fun fetchHttpBytes(url: String): ByteArray? {
        val connection = URI.create(url).toURL().openConnection() as? HttpURLConnection ?: return null
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 4_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "image/*,*/*;q=0.8")
        connection.setRequestProperty("Connection", "close")

        return try {
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.use { it.readAllBytes() }
            } else {
                logger.debug("Artwork HTTP request failed with status {} for {}", code, url)
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun evictIfNeeded() {
        if (cache.size <= MAX_ENTRIES) {
            return
        }

        val removable = cache.entries
            .filter { it.value.state != State.LOADING }
            .sortedBy { it.value.lastAccess }
            .take((cache.size - MAX_ENTRIES).coerceAtLeast(1))

        if (removable.isEmpty()) {
            return
        }

        val client = Minecraft.getInstance()
        removable.forEach { (key, value) ->
            if (cache.remove(key, value)) {
                client.execute {
                    client.textureManager.release(value.identifier)
                }
            }
        }
    }

    private data class Entry(
        val identifier: Identifier,
        @Volatile var state: State,
        @Volatile var lastAccess: Long,
        @Volatile var lastFailure: Long = 0L
    )

    private enum class State {
        LOADING,
        READY,
        FAILED
    }
}
