package meowing.zen.features.meowing

import meowing.zen.Zen
import meowing.zen.config.ui.ConfigUI
import meowing.zen.config.ui.types.ConfigElement
import meowing.zen.config.ui.types.ElementType
import meowing.zen.events.ChatEvent
import meowing.zen.features.Feature
import meowing.zen.utils.ChatUtils
import meowing.zen.utils.CommandUtils
import meowing.zen.utils.DataUtils
import com.mojang.brigadier.context.CommandContext
import meowing.zen.Zen.Companion.prefix
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

data class Data(val meowcount: Double = 0.0)

@Zen.Module
object meowcount : Feature("meowcount") {
    private val dataUtils = DataUtils("meowcount", Data())

    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("Meowing", "Meow count", ConfigElement(
                "meowcount",
                "Meow count",
                ElementType.Switch(false)
            ), isSectionToggle = true)
    }

    override fun initialize() {
        register<ChatEvent.Send> { event ->
            if (event.message.lowercase().contains("meow")) {
                val currentData = dataUtils.getData()
                val newData = currentData.copy(currentData.meowcount + 1.0)
                dataUtils.setData(newData)
            }
        }
    }

    fun getMeowCount(): Double = dataUtils.getData().meowcount
}

@Zen.Command
object MeowCommand : CommandUtils("meowcount", aliases = listOf("zenmeow", "zenmeowcount")) {
    override fun execute(context: CommandContext<FabricClientCommandSource>): Int {
        val count = meowcount.getMeowCount().toInt()
        ChatUtils.addMessage("$prefix §fYou have meowed §b$count §ftimes!")
        return 1
    }
}