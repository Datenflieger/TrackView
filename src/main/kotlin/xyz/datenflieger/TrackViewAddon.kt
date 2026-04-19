package xyz.datenflieger

import com.dwarslooper.cactus.client.addon.v2.ICactusAddon
import com.dwarslooper.cactus.client.addon.v2.RegistryBus
import com.dwarslooper.cactus.client.gui.hud.element.HudElement
import xyz.datenflieger.trackview.hud.TrackViewHudElement
import xyz.datenflieger.trackview.media.TrackViewMediaService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TrackViewAddon : ICactusAddon {
    override fun onInitialize(registryBus: RegistryBus) {
        TrackViewMediaService.start()
        registryBus.register(HudElement::class.java) { TrackViewHudElement() }
        LOGGER.info("TrackView addon loaded")
    }

    override fun onShutdown() {
        TrackViewMediaService.stop()
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LoggerFactory.getLogger("TrackView")
    }
}
