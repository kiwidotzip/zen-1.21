package meowing.zen.events

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.util.ActionResult
import org.lwjgl.glfw.GLFW
import java.util.concurrent.ConcurrentHashMap

object EventBus {
    val listeners = ConcurrentHashMap<Class<*>, MutableSet<Any>>()

    init {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            post(TickEvent.Client())
        }

        ClientEntityEvents.ENTITY_LOAD.register { entity, _ ->
            post(EntityEvent.Join(entity))
        }

        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            post(EntityEvent.Leave(entity))
        }

        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { mc, world ->
            post(WorldEvent.Change(world))
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { msg, show ->
            val customEvent = when (show) {
                true -> GameEvent.ActionBar(msg)
                false -> ChatEvent.Receive(msg)
            }

            !post(customEvent)
        }

        ClientSendMessageEvents.ALLOW_CHAT.register { string ->
            !post(ChatEvent.Send(string))
        }

        ClientSendMessageEvents.ALLOW_COMMAND.register { string ->
            val command = string.split(" ")[0].lowercase()
            when (command) {
                "gc", "pc", "ac", "msg", "tell", "r", "say", "w", "reply" -> !post(ChatEvent.Send("/$string"))
                else -> true
            }
        }

        WorldRenderEvents.LAST.register { context ->
            post(RenderEvent.World(context))
        }

        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            post(RenderEvent.WorldPostEntities(context))
        }

        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            ScreenMouseEvents.allowMouseClick(screen).register { _, mx, my, mbtn ->
                !post(GuiEvent.Click(mx, my, mbtn, true, screen))
            }

            ScreenMouseEvents.allowMouseRelease(screen).register { _, mx, my, mbtn ->
                !post(GuiEvent.Click(mx, my, mbtn, false, screen))
            }

            ScreenKeyboardEvents.allowKeyPress(screen).register { _, key, scancode, _ ->
                !post(GuiEvent.Key(GLFW.glfwGetKeyName(key, scancode), key, scancode, screen))
            }
            ScreenEvents.remove(screen).register { screen ->
                post(GuiEvent.Close(screen))
            }
            ScreenEvents.afterRender(screen).register { _, context, mouseX, mouseY, tickDelta ->
                post(GuiEvent.AfterRender(screen, context))
            }
        }

        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (screen != null) post(GuiEvent.Open(screen))
        }

        WorldRenderEvents.BLOCK_OUTLINE.register { worldContext, blockContext ->
            !post(RenderEvent.BlockOutline(worldContext, blockContext))
        }

        ClientLifecycleEvents.CLIENT_STARTED.register { _ ->
            post(GameEvent.Load())
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register { _ ->
            post(GameEvent.Unload())
        }

        UseItemCallback.EVENT.register { player, world, hand ->
            post(EntityEvent.Interact(player, world, hand, "USE_ITEM"))
            ActionResult.PASS
        }

        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            post(EntityEvent.Interact(player, world, hand, "USE_BLOCK", hitResult.blockPos))
            ActionResult.PASS
        }

        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            post(EntityEvent.Interact(player, world, hand, "USE_ENTITY"))
            ActionResult.PASS
        }

        AttackBlockCallback.EVENT.register { player, world, hand, pos, direction ->
            post(EntityEvent.Interact(player, world, hand, "ATTACK_BLOCK", pos))
            ActionResult.PASS
        }

        AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            post(EntityEvent.Interact(player, world, hand, "ATTACK_ENTITY"))
            ActionResult.PASS
        }
    }

    fun onPacketReceived(packet: Packet<*>) {
        post(PacketEvent.Received(packet))
        PacketReceived(packet)
    }

    fun onPacketSent(packet: Packet<*>) {
        post(PacketEvent.Sent(packet))
    }

    private fun PacketReceived(packet: Packet<*>) {
        when (packet) {
            is CommonPingS2CPacket -> {
                post(TickEvent.Server())
            }
            is EntityTrackerUpdateS2CPacket -> {
                post(EntityEvent.Metadata(packet))
            }
            is EntitySpawnS2CPacket -> {
                post(EntityEvent.Spawn(packet))
            }
            is ScoreboardObjectiveUpdateS2CPacket, is ScoreboardScoreUpdateS2CPacket, is ScoreboardDisplayS2CPacket, is TeamS2CPacket -> {
                post(ScoreboardEvent.Update(packet))
            }
            is PlayerListS2CPacket -> {
                when (packet.actions.firstOrNull()) {
                    PlayerListS2CPacket.Action.ADD_PLAYER, PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME -> {
                        post(TablistEvent.Update(packet))
                    }
                    else -> {}
                }
            }
        }
    }

    /*
     * Modified from Devonian code
     * Under GPL 3.0 License
     */
    inline fun <reified T : Event> register(noinline callback: (T) -> Unit, add: Boolean = true): EventCall {
        val eventClass = T::class.java
        val handlers = listeners.getOrPut(eventClass) { ConcurrentHashMap.newKeySet() }
        if (add) handlers.add(callback)
        return EventCallImpl(callback, handlers)
    }

    fun <T : Event> post(event: T): Boolean {
        val eventClass = event::class.java
        val handlers = listeners[eventClass] ?: return false
        if (handlers.isEmpty()) return false

        for (handler in handlers) {
            try {
                @Suppress("UNCHECKED_CAST")
                (handler as (T) -> Unit)(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return if (event is CancellableEvent) event.isCancelled() else false
    }

    class EventCallImpl(
        private val callback: Any,
        private val handlers: MutableSet<Any>
    ) : EventCall {
        override fun unregister(): Boolean = handlers.remove(callback)
        override fun register(): Boolean = handlers.add(callback)
    }

    interface EventCall {
        fun unregister(): Boolean
        fun register(): Boolean
    }
}