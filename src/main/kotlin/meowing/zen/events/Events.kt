package meowing.zen.events

import meowing.zen.api.EntityDetection
import meowing.zen.api.ItemAbility
import meowing.zen.api.PartyTracker.PartyMember
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

abstract class Event

abstract class CancellableEvent : Event() {
    private var cancelled = false
    fun cancel() { cancelled = true }
    fun isCancelled() = cancelled
}

abstract class SkyblockEvent {
    class ItemAbilityUsed(val ability: ItemAbility.ItemAbility) : Event()
    class EntitySpawn(val skyblockMob: EntityDetection.SkyblockMob) : Event()
}

abstract class MouseEvent {
    class Click(val button: Int) : Event()
    class Release(val button: Int) : Event()
    class Scroll(val event: MouseEvent) : Event()
    class Move(val event: MouseEvent) : Event()
}

abstract class TickEvent {
    class Client : Event()
    class Server : Event()
}

abstract class GameEvent {
    class Load : Event()
    class Unload : Event()
    class ActionBar(val message: Text) : CancellableEvent()
}

abstract class PartyEvent {
    class Changed(val type: PartyChangeType, val playerName: String? = null, val members: Map<String, PartyMember>) : Event()
}

enum class PartyChangeType {
    MEMBER_JOINED, MEMBER_LEFT, PLAYER_JOINED, PLAYER_LEFT, LEADER_CHANGED, DISBANDED, LIST, PARTY_FINDER
}

abstract class RenderEvent {
    class World(val context: WorldRenderContext?) : Event()
    class WorldPostEntities(val context: WorldRenderContext?) : Event()
    class EntityPre(val entity: Entity, val matrices: MatrixStack, val vertex: VertexConsumerProvider, val light: Int) : CancellableEvent()
    class EntityPost(val entity: Entity, val matrices: MatrixStack, val vertex: VertexConsumerProvider, val light: Int) : Event()
    class PlayerPre(val entity: PlayerEntityRenderState, val matrices: MatrixStack) : CancellableEvent()
    class BlockOutline(val worldContext: WorldRenderContext, val blockContext: WorldRenderContext.BlockOutlineContext) : CancellableEvent()
    class EntityGlow(val entity: Entity, var shouldGlow: Boolean, var glowColor: Int) : Event()
}

abstract class EntityEvent {
    class Join(val entity: Entity) : Event()
    class Leave(val entity: Entity) : Event()
    class Death(val entity: Entity) : Event()
    class Attack(val player: PlayerEntity, val target: Entity) : Event()
    class Metadata(val packet: EntityTrackerUpdateS2CPacket) : Event()
    class Spawn(val packet: EntitySpawnS2CPacket) : Event()
    class Interact(val player: PlayerEntity, val world: World, val hand: Hand, val action: String, val pos: BlockPos? = null) : Event()
}

abstract class GuiEvent {
    class AfterRender(val screen: Screen, val context: DrawContext) : Event()
    class HUD(val context: DrawContext) : Event()
    class Open(val screen: Screen) : Event()
    class Close(val screen: Screen) : Event()
    class Click(val mx: Double, val my: Double, val mbtn: Int, val state: Boolean, val screen: Screen) : CancellableEvent()
    class Key(val keyName: String?, val key: Int, val scanCode: Int, val screen: Screen) : CancellableEvent()
    abstract class Slot {
        class Click(val slot: net.minecraft.screen.slot.Slot?, val slotId: Int, val button: Int, val actionType: SlotActionType, val handler: ScreenHandler, val screen: HandledScreen<*>) : CancellableEvent()
        class Render(val context: DrawContext, val slot: net.minecraft.screen.slot.Slot) : Event()
    }
}

abstract class ChatEvent {
    class Receive(val message: Text) : CancellableEvent()
    class Send(val message: String) : CancellableEvent()
}

abstract class WorldEvent {
    class Change(val world: ClientWorld) : Event()
}

abstract class PacketEvent {
    class Received(val packet: Packet<*>) : Event()
    class Sent(val packet: Packet<*>) : Event()
}

abstract class ScoreboardEvent {
    class Update(val packet: Packet<*>) : Event()
}

abstract class TablistEvent {
    class Update(val packet: PlayerListS2CPacket) : Event()
}

abstract class AreaEvent {
    class Main(val area: String) : Event()
    class Sub(val subarea: String) : Event()
}