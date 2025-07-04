package meowing.zen.feats.slayers

import meowing.zen.Zen
import meowing.zen.Zen.Companion.mc
import meowing.zen.config.ui.ConfigUI
import meowing.zen.config.ui.types.ConfigElement
import meowing.zen.config.ui.types.ElementType
import meowing.zen.events.RenderEvent
import meowing.zen.feats.Feature
import meowing.zen.utils.RenderUtils
import meowing.zen.utils.Utils
import meowing.zen.utils.Utils.toColorFloat
import java.awt.Color

object slayerhighlight : Feature("slayerhighlight") {
    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("Slayers", "General", ConfigElement(
                "slayerhighlight",
                "Slayer highlight",
                "Highlights your slayer boss.",
                ElementType.Switch(false)
            ))
    }

    override fun initialize() {
        var color = Color(0, 255, 255, 127)
        var filled = false

        Zen.registerCallback("slayerhighlightfilled") { newval ->
            filled = newval as Boolean
        }

        Zen.registerCallback("slayerhighlightcolor") { newval ->
            color = newval as Color
        }

        register<RenderEvent.EntityPre> { event ->
            if (!slayertimer.isFighting || slayertimer.BossId == -1 || event.entity.id != slayertimer.BossId) return@register
            val entity = event.entity
            val width = entity.width + 0.2
            val height = entity.height
            val entityPos = entity.getLerpedPos(Utils.getPartialTicks())
            val cam = mc.gameRenderer.camera
            val x = entityPos.x - cam.pos.x
            val y = entityPos.y - cam.pos.y
            val z = entityPos.z - cam.pos.z

            if (filled) {
                RenderUtils.renderEntityFilled(
                    event.matrices,
                    event.vertex,
                    x,
                    y,
                    z,
                    width.toFloat(),
                    height,
                    color.red.toColorFloat(),
                    color.green.toColorFloat(),
                    color.blue.toColorFloat(),
                    color.alpha.toColorFloat()
                )
            } else {
                RenderUtils.renderEntityOutline(
                    event.matrices,
                    event.vertex,
                    x,
                    y,
                    z,
                    width,
                    height,
                    color.red.toColorFloat(),
                    color.green.toColorFloat(),
                    color.blue.toColorFloat(),
                    color.alpha.toColorFloat()
                )
            }
        }
    }
}