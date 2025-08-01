package meowing.zen.features.general

import meowing.zen.Zen
import meowing.zen.Zen.Companion.prefix
import meowing.zen.api.EntityDetection.sbMobID
import meowing.zen.config.ConfigDelegate
import meowing.zen.config.ui.ConfigUI
import meowing.zen.config.ui.types.ConfigElement
import meowing.zen.config.ui.types.ElementType
import meowing.zen.events.MouseEvent
import meowing.zen.events.RenderEvent
import meowing.zen.features.Feature
import meowing.zen.utils.ChatUtils
import meowing.zen.utils.Utils.toColorInt
import net.minecraft.entity.Entity
import net.minecraft.util.hit.EntityHitResult
import java.awt.Color

@Zen.Module
object BestiaryMobHighlight : Feature("bestiarymobhighlighter") {
    private val trackedMobs = mutableListOf<String>()
    private val highlightcolor by ConfigDelegate<Color>("bestiarymobhighlightcolor")

    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("General", "Bestiary Mob Highlight", "Options", ConfigElement(
                "bestiarymobhighlighter",
                null,
                ElementType.Switch(false)
            ), isSectionToggle = true)
            .addElement("General", "Bestiary Mob Highlight", "Options", ConfigElement(
                "bestiarymobhighlightcolor",
                "Highlight color",
                ElementType.ColorPicker(Color(0, 255, 255, 127))
            ))
    }

    override fun initialize() {
        register<RenderEvent.EntityGlow> { event ->
            val mob = event.entity.sbMobID ?: return@register
            if (trackedMobs.contains(mob)) {
                event.shouldGlow = true
                event.glowColor = highlightcolor.toColorInt()
            }
        }

        register<MouseEvent.Click> { event ->
            if (event.button == 2) {
                val mob = getTargetEntity() ?: return@register
                val id = mob.sbMobID ?: return@register ChatUtils.addMessage("$prefix §cThis mob could not be identified for the bestiary tracker!")
                if (trackedMobs.contains(id)) {
                    trackedMobs.remove(id)
                    ChatUtils.addMessage("$prefix §cStopped highlighting ${id}!")
                } else {
                    trackedMobs.add(id)
                    ChatUtils.addMessage("$prefix §aStarted highlighting ${id}!")
                }
            }
        }
    }

    private fun getTargetEntity(): Entity? {
        val crosshairTarget = mc.crosshairTarget ?: return null
        return if (crosshairTarget is EntityHitResult) crosshairTarget.entity else null
    }
}