package xyz.datenflieger

import com.dwarslooper.cactus.client.addon.v2.ICactusAddon
import com.dwarslooper.cactus.client.addon.v2.RegistryBus
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TrackViewAddon : ICactusAddon {
    override fun onInitialize(registryBus: RegistryBus) {
        LOGGER.info("TrackView addon loaded")
    }

    override fun onLoadComplete() {
    }

    override fun onShutdown() {
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LoggerFactory.getLogger("TrackView")
    }
}
