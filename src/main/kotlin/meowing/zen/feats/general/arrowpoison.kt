package meowing.zen.feats.general

import meowing.zen.Zen
import meowing.zen.Zen.Companion.mc
import meowing.zen.config.ui.ConfigUI
import meowing.zen.config.ui.types.ConfigElement
import meowing.zen.config.ui.types.ElementType
import meowing.zen.events.GuiEvent
import meowing.zen.events.PacketEvent
import meowing.zen.feats.Feature
import meowing.zen.hud.HUDManager
import meowing.zen.utils.ChatUtils
import meowing.zen.utils.Render2D
import meowing.zen.utils.Utils.removeFormatting
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket

@Zen.Module
object arrowpoison : Feature("arrowpoison") {
    private const val name = "ArrowPoison"
    private var twilight = 0
    private var toxic = 0

    override fun addConfig(configUI: ConfigUI): ConfigUI {
        return configUI
            .addElement("General", "Arrow poison tracker", ConfigElement(
                "arrowpoison",
                "Arrow poison tracker",
                "Tracks the arrow poisons inside your inventory.",
                ElementType.Switch(false)
            ))
    }

    override fun initialize() {
        HUDManager.register(name, "<I> 64 | <I> 32")

        register<PacketEvent.Received> { event ->
            if (event.packet is InventoryS2CPacket || event.packet is SetPlayerInventoryS2CPacket || event.packet is ScreenHandlerSlotUpdateS2CPacket) updateCount()
        }

        register<GuiEvent.HUD> { render(it.context) }
    }

    private fun updateCount() {
        twilight = 0
        toxic = 0
        if (mc.player == null || mc?.player?.inventory == null) return
        mc.player!!.inventory.mainStacks.forEach { item ->
            if (item == null) return@forEach
            val name = item.name.string.removeFormatting()
            if (name.contains("Twilight Arrow Poison")) twilight += item.count
            if (name.contains("Toxic Arrow Poison")) toxic += item.count
        }
    }

    private fun render(drawContext: DrawContext) {
        if (twilight == 0 && toxic == 0) return

        val x = HUDManager.getX(name)
        val y = HUDManager.getY(name)
        val scale = HUDManager.getScale(name)

        drawHUD(drawContext, x, y, scale)
    }

    private fun drawHUD(drawContext: DrawContext, x: Float, y: Float, scale: Float) {
        val iconSize = 16f * scale
        val spacing = 4f * scale
        val twilightPotion = ItemStack(Items.PURPLE_DYE)
        val toxicPotion = ItemStack(Items.LIME_DYE)
        val fontRenderer = mc.textRenderer
        val twilightStr = twilight.toString()
        val toxicStr = toxic.toString()
        val textY = y + (iconSize - 8f * scale) / 2f
        var currentX = x

        Render2D.renderItem(drawContext, twilightPotion, currentX, y, scale)

        currentX += iconSize + spacing
        Render2D.renderStringWithShadow(drawContext, twilightStr, currentX, textY, scale)

        currentX += fontRenderer.getWidth(twilightStr) * scale + spacing * 2
        Render2D.renderStringWithShadow(drawContext, "§7|", currentX, textY, scale)

        currentX += fontRenderer.getWidth("|") * scale + spacing
        Render2D.renderItem(drawContext, toxicPotion, currentX, y, scale)

        currentX += iconSize + spacing
        Render2D.renderStringWithShadow(drawContext, toxicStr, currentX, textY, scale)
    }
}