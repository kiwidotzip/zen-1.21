package meowing.zen.feats.meowing

import meowing.zen.Zen
import meowing.zen.feats.Feature
import meowing.zen.Zen.Companion.mc
import meowing.zen.config.ui.ConfigUI
import meowing.zen.config.ui.types.ConfigElement
import meowing.zen.config.ui.types.ElementType
import meowing.zen.events.ChatEvent
import meowing.zen.utils.Utils.removeFormatting
import net.minecraft.sound.SoundEvents

@Zen.Module
object meowsounds : Feature("meowsounds") {
    private val meowRegex = Regex("(?:Guild|Party|Co-op|From|To)? ?>? ?(?:\\[.+?])? ?[a-zA-Z0-9_]+ ?(?:\\[.+?])?: (.+)")

    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("Meowing", "Meow Sounds", ConfigElement(
                "meowdeathsounds",
                "Meow Death Sounds",
                "Plays a cat sound whenever an entity dies",
                ElementType.Switch(false)
            ))
    }

    override fun initialize() {
        register<ChatEvent.Receive> {
            val content = it.message.string.removeFormatting().lowercase()
            val match = meowRegex.find(content) ?: return@register
            if (match.groups[1]?.value?.contains("meow", ignoreCase = true) != true) return@register

            mc.player?.let { player ->
                mc.world?.playSound(
                    null,
                    player.pos.x, player.pos.y, player.pos.z,
                    SoundEvents.ENTITY_CAT_AMBIENT,
                    net.minecraft.sound.SoundCategory.AMBIENT,
                    0.8f, 1.0f
                )
            }
        }
    }
}