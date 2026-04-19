package com.dwarslooper.cactus.addon_template

import com.dwarslooper.cactus.addon_template.feature.commands.ExampleCommand
import com.dwarslooper.cactus.addon_template.feature.modules.ExampleModule
import com.dwarslooper.cactus.client.addon.v2.ICactusAddon
import com.dwarslooper.cactus.client.addon.v2.RegistryBus
import com.dwarslooper.cactus.client.feature.command.Command
import com.dwarslooper.cactus.client.feature.module.Category
import com.dwarslooper.cactus.client.feature.module.Module
import net.minecraft.world.item.Items
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AddonTemplateMain : ICactusAddon {
    override fun onInitialize(registryBus: RegistryBus) {
        LOGGER.info("Hello, Cactus!")

        registryBus.register(Category::class.java) { CATEGORY }
        registryBus.register(Module::class.java) { ExampleModule() }
        registryBus.register(Command::class.java) { ExampleCommand() }

        LOGGER.info("Template Addon successfully loaded!")
    }

    override fun onLoadComplete() {
    }

    override fun onShutdown() {
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LoggerFactory.getLogger("Cactus Addon Template")

        @JvmField
        val CATEGORY: Category = Category("exampleCategory", Items.DIAMOND.defaultInstance)
    }
}
