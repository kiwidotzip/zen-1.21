package meowing.zen.api

import meowing.zen.Zen
import meowing.zen.Zen.Companion.mc
import meowing.zen.events.EntityEvent
import meowing.zen.events.EventBus
import meowing.zen.events.SkyblockEvent
import meowing.zen.events.WorldEvent
import meowing.zen.utils.TickUtils
import meowing.zen.utils.Utils.removeFormatting
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.projectile.ArrowEntity

@Zen.Module
object EntityDetection {
    private val hashMap = HashMap<Entity, SkyblockMob>()
    private val normalMobRegex = "\\[Lv(?:\\d+k?)] (?:[༕ൠ☮⊙ŽŽ✰♨⚂❆☽✿☠⸕⚓♆♣⚙︎♃⛨✈⸙] )?(.+?) [\\d.,]+[MkB]?/[\\d.,]+[MkB]?❤".toRegex()
    private val slayerMobRegex = "(?<=☠\\s)[A-Za-z]+\\s[A-Za-z]+(?:\\s[IVX]+)?".toRegex()
    private val dungeonMobRegex = "(?:[༕ൠ☮⊙ŽŽ✰♨⚂❆☽✿☠⸕⚓♆♣⚙︎♃⛨✈⸙] )?✯?\\s*(?:Flaming|Super|Healing|Boomer|Golden|Speedy|Fortified|Stormy|Healthy)?\\s*([\\w\\s]+?)\\s*([\\d.,]+[mkM?]*|[?]+)❤".toRegex()
    private val patterns = listOf(normalMobRegex, slayerMobRegex, dungeonMobRegex)

    class SkyblockMob(val nameEntity: Entity, val skyblockMob: Entity) {
        var id: String? = null
    }

    init {
        TickUtils.loop(5) {
            val world = mc.world ?: return@loop
            val player = mc.player ?: return@loop

            world.entities.forEach { entity ->
                if (player.distanceTo(entity) > 30 || entity !is ArmorStandEntity || !entity.hasCustomName() || hashMap.containsKey(entity)) return@forEach
                val nameTag = entity.name.string
                val mobId = if (nameTag.contains("Withermancer")) entity.id - 3 else entity.id - 1
                val mob = world.getEntityById(mobId) ?: return@forEach

                if (!mob.isAlive || mob is ArrowEntity) return@forEach

                val skyblockMob = SkyblockMob(entity, mob)
                hashMap[entity] = skyblockMob
                updateMobData(skyblockMob)

                if (skyblockMob.id != null) {
                    EventBus.post(SkyblockEvent.EntitySpawn(skyblockMob))
                }
            }
        }

        EventBus.register<WorldEvent.Change> ({
            hashMap.clear()
        })

        EventBus.register<EntityEvent.Death> ({ event ->
            hashMap.remove(event.entity)
            hashMap.entries.removeAll { it.value.skyblockMob == event.entity }
        })
    }

    private fun updateMobData(sbMob: SkyblockMob) {
        val rawMobName = sbMob.nameEntity.displayName?.string.removeFormatting().replace(",", "")

        patterns.forEachIndexed { index, pattern ->
            pattern.find(rawMobName)?.let { match ->
                sbMob.id = when (index) {
                    0 -> match.groupValues[1]
                    1 -> "${match.value} Slayer"
                    2 -> {
                        val mobName = match.groupValues[1]
                        if (rawMobName.startsWith("ൠ")) "$mobName Pest" else mobName
                    }
                    else -> return
                }

                sbMob.id?.let { id ->
                    if (id.startsWith("a") && id.length > 2 && Character.isUpperCase(id[1])) {
                        sbMob.id = id.substring(1, id.length - 2)
                    }
                }
                return
            }
        }
    }

    inline val Entity.sbMobID: String? get() = getSkyblockMob(this)?.id

    fun getSkyblockMob(entity: Entity): SkyblockMob? = hashMap.values.firstOrNull { it.skyblockMob == entity }
    fun getNameTag(entity: Entity): SkyblockMob? = hashMap.values.firstOrNull { it.nameEntity == entity }
}