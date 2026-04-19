package xyz.datenflieger.trackview.hud

import com.dwarslooper.cactus.client.gui.hud.element.DynamicHudElement
import com.dwarslooper.cactus.client.systems.config.settings.impl.BooleanSetting
import com.dwarslooper.cactus.client.systems.config.settings.impl.IntegerSetting
import com.dwarslooper.cactus.client.systems.config.settings.impl.Setting
import com.dwarslooper.cactus.client.util.CactusConstants.mc
import com.dwarslooper.cactus.client.util.generic.TextUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import org.endlesssource.mediainterface.api.PlaybackState
import org.joml.Vector2i
import xyz.datenflieger.trackview.media.TrackViewArtworkCache
import xyz.datenflieger.trackview.media.TrackViewMediaService
import xyz.datenflieger.trackview.media.TrackViewSnapshot
import kotlin.math.max

class TrackViewHudElement : DynamicHudElement<TrackViewHudElement>("trackView", Vector2i(220, 68), Vector2i(140, 34)) {
    private val showThumbnail: Setting<Boolean> = elementGroup.add(BooleanSetting("showThumbnail", true))
    private val imageSize: Setting<Int> = elementGroup.add(IntegerSetting("imageSize", 38).min(20).max(96)).visibleIf(showThumbnail::get).setCallback { correct() }
    private val showSourceApp: Setting<Boolean> = elementGroup.add(BooleanSetting("showSourceApp", true))
    private val showProgressBar: Setting<Boolean> = elementGroup.add(BooleanSetting("showProgressBar", true))
    private val hideWhenNoMedia: Setting<Boolean> = elementGroup.add(BooleanSetting("hideWhenNoMedia", true))
    private val compactMode: Setting<Boolean> = elementGroup.add(BooleanSetting("compactMode", false)).setCallback { correct() }

    private var activeSnapshot: TrackViewSnapshot = TrackViewSnapshot.empty()

    private val accentTitleColor = 0xFFCECECE.toInt()
    private val accentSourceColor = 0xFF9F9F9F.toInt()
    private val placeholderColor = 0x55222222
    private val placeholderNoteColor = 0xFFDDDDDD.toInt()
    private val progressBackgroundColor = 0x55333333
    private val progressForegroundColor = 0xFF81C941.toInt()

    private val padding = 4
    private val thumbnailGap = 6
    private val lineSpacing = 2
    private val compactExtraSourceOffset = 1
    private val defaultSourceOffset = 2
    private val progressBarHeight = 2
    private val progressBarBottomOffset = 6

    private val editorSnapshot = TrackViewSnapshot(
        hasMedia = true,
        title = "Nightdrive Memories",
        artist = "Atlas Harbor",
        sourceApp = "Spotify",
        playbackState = PlaybackState.PLAYING,
        durationMillis = 212_000,
        positionMillis = 97_000,
        artworkToken = null,
        updatedAt = System.currentTimeMillis()
    )

    override fun canResize(): Boolean {
        return true
    }

    override fun duplicate(): TrackViewHudElement {
        return TrackViewHudElement()
    }

    override fun created() {
        TrackViewMediaService.start()
    }

    override fun render(context: GuiGraphicsExtractor, x: Int, y: Int, screenWidth: Int, screenHeight: Int, delta: Float, inEditor: Boolean) {
        val snapshot = if (inEditor) editorSnapshot else TrackViewMediaService.snapshot()
        if (!inEditor && hideWhenNoMedia.get() && !snapshot.hasMedia) {
            return
        }

        activeSnapshot = snapshot
        super.render(context, x, y, screenWidth, screenHeight, delta, inEditor)
    }

    override fun renderContent(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        screenWidth: Int,
        screenHeight: Int,
        delta: Float,
        inEditor: Boolean
    ) {
        val snapshot = activeSnapshot
        val textColorValue = textColor.get().color()

        if (!snapshot.hasMedia) {
            drawNoMediaFallback(context, x, y, width, height, textColorValue)
            return
        }

        val compact = compactMode.get()
        val thumbSize = resolveThumbnailSize(compact, height)

        var drawX = x + padding
        val drawY = y + padding

        if (thumbSize > 0) {
            renderThumbnail(context, snapshot, drawX, drawY, thumbSize)
            drawX += thumbSize + thumbnailGap
        }

        val textSpace = max(30, width - (drawX - x) - padding)
        val statePrefix = statePrefix(snapshot.playbackState)

        val rawTitle = snapshot.title.ifBlank {
            snapshot.sourceApp.ifBlank { "Unknown title" }
        }
        val rawArtist = snapshot.artist.ifBlank {
            snapshot.playbackState.name.lowercase().replaceFirstChar { it.titlecase() }
        }
        val title = TextUtils.trimToWidth("$statePrefix $rawTitle", textSpace, "...")
        val artist = TextUtils.trimToWidth(rawArtist, textSpace, "...")

        context.text(mc.font, title, drawX, drawY, textColorValue, textShadows())

        val secondLineY = drawY + mc.font.lineHeight + lineSpacing
        context.text(mc.font, artist, drawX, secondLineY, accentTitleColor, textShadows())

        if (showSourceApp.get()) {
            val source = snapshot.sourceApp.ifBlank { "Unknown player" }
            val sourceY = secondLineY + mc.font.lineHeight + if (compact) compactExtraSourceOffset else defaultSourceOffset
            val sourceText = TextUtils.trimToWidth(source, textSpace, "...")
            context.text(mc.font, sourceText, drawX, sourceY, accentSourceColor, textShadows())
        }

        if (showProgressBar.get() && snapshot.durationMillis != null && snapshot.durationMillis > 0L && snapshot.positionMillis != null) {
            val barWidth = max(16, textSpace)
            val barX = drawX
            val barY = y + height - progressBarBottomOffset
            val progress = (snapshot.positionMillis.coerceIn(0L, snapshot.durationMillis).toDouble() / snapshot.durationMillis.toDouble()).toFloat()
            val fillWidth = (barWidth * progress).toInt().coerceIn(0, barWidth)

            context.fill(barX, barY, barX + barWidth, barY + progressBarHeight, progressBackgroundColor)
            context.fill(barX, barY, barX + fillWidth, barY + progressBarHeight, progressForegroundColor)
        }
    }

    override fun getEffectiveSize(): Vector2i {
        return getSize()
    }

    private fun drawNoMediaFallback(context: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        context.centeredText(mc.font, "No active media", x + width / 2, y + (height - mc.font.lineHeight) / 2 + 1, color)
    }

    private fun resolveThumbnailSize(compact: Boolean, height: Int): Int {
        val base = when {
            !showThumbnail.get() -> 0
            compact -> imageSize.get().coerceAtMost(32)
            else -> imageSize.get()
        }

        return base.coerceAtMost((height - padding * 2).coerceAtLeast(0))
    }

    private fun renderThumbnail(context: GuiGraphicsExtractor, snapshot: TrackViewSnapshot, x: Int, y: Int, size: Int) {
        val artworkId = snapshot.artworkToken?.let(TrackViewArtworkCache::request)
        if (artworkId != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, artworkId, x, y, 0f, 0f, size, size, size, size)
            return
        }

        context.fill(x, y, x + size, y + size, placeholderColor)
        context.centeredText(mc.font, "♪", x + size / 2, y + (size - mc.font.lineHeight) / 2 + 1, placeholderNoteColor)
    }

    private fun statePrefix(state: PlaybackState): String {
        return when (state) {
            PlaybackState.PLAYING -> "▶"
            PlaybackState.PAUSED -> "II"
            PlaybackState.STOPPED -> "■"
            PlaybackState.UNKNOWN -> "•"
        }
    }
}
