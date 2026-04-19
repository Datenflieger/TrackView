package com.dwarslooper.cactus.addon_template.feature.modules

import com.dwarslooper.cactus.addon_template.AddonTemplateMain
import com.dwarslooper.cactus.client.event.EventHandler
import com.dwarslooper.cactus.client.event.impl.ClientTickEvent
import com.dwarslooper.cactus.client.feature.module.Module
import com.dwarslooper.cactus.client.systems.config.settings.group.SettingGroup
import com.dwarslooper.cactus.client.systems.config.settings.impl.BooleanSetting
import com.dwarslooper.cactus.client.systems.config.settings.impl.Setting
import com.dwarslooper.cactus.client.util.game.ChatUtils

class ExampleModule : Module("exampleModule", AddonTemplateMain.CATEGORY, Options()) {
    private val exampleGroup: SettingGroup = settings.buildGroup("example")
    val sendGreetings: Setting<Boolean> = exampleGroup.add(BooleanSetting("sendGreetings", true))

    override fun onEnable() {
        if (sendGreetings.get()) {
            ChatUtils.infoPrefix("Example Module", "Hello, Example Module")
        }
    }

    override fun onDisable() {
        if (sendGreetings.get()) {
            ChatUtils.infoPrefix("Example Module", "See ya later, Example Module")
        }
    }

    @EventHandler
    fun onTick(event: ClientTickEvent) {
    }
}
