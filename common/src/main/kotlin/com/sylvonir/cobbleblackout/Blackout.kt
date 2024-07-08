package com.sylvonir.cobbleblackout

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.toVec3d
import net.minecraft.block.Blocks
import net.minecraft.entity.EntityPose
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket
import net.minecraft.registry.tag.BlockTags
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import java.util.*

object Blackout {
    const val MOD_ID = "cobbleblackout"

    fun init() {
        CobblemonEvents.BATTLE_VICTORY.subscribe { handleVictoryEvent(it) }
    }

    private fun handleVictoryEvent(victoryEvent: BattleVictoryEvent) {
        val battle = victoryEvent.battle
        val losers = victoryEvent.losers

        if (battle.isPvP || victoryEvent.wasWildCapture) {
            return
        }
        losers.filterIsInstance<PlayerBattleActor>().forEach {
            val player = it.entity ?: return@forEach
            if (player.abilities.creativeMode) return@forEach
            //val activeStatusEffects = HashMap(player.activeStatusEffects)

            //val networkHandler = player.networkHandler
            //networkHandler.player = player.getServer()?.playerManager?.respawnPlayer(player, true) ?: return
            //player = networkHandler.player

            //MC-6431 fix, will be unnecessary in 1.21
            //player.activeStatusEffects.putAll(activeStatusEffects)
            //player.activeStatusEffects.forEach {
            //    player.networkHandler.sendPacket(EntityStatusEffectS2CPacket(player.id, it.value))
            //}

            player.addStatusEffect(StatusEffectInstance(StatusEffects.BLINDNESS, 40))
            player.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3))

            var blockPos = player.spawnPointPosition
            var angle = player.spawnAngle
            val forced = player.isSpawnForced
            var optional = Optional.empty<Vec3d>()
            var respawnWorld = player.getServer()?.getWorld(player.spawnPointDimension)

            if (blockPos != null && respawnWorld != null) {
                optional = PlayerEntity.findRespawnPosition(respawnWorld, blockPos, angle, forced, true)
            } else {
                respawnWorld = player.getServer()?.overworld
            }

            if (respawnWorld != null) {
                if (optional.isPresent) {
                    val blockState = respawnWorld.getBlockState(blockPos)
                    if (blockState != null && (blockState.isIn(BlockTags.BEDS) || blockState.isOf(Blocks.RESPAWN_ANCHOR))) {
                        val vec3d = Vec3d.ofBottomCenter(blockPos).subtract(optional.get()).normalize()
                        angle = MathHelper.wrapDegrees(MathHelper.atan2(vec3d.z, vec3d.x) * 57.2957763671875 - 90.0)
                            .toFloat()
                    }
                } else if (blockPos != null) {
                    blockPos = respawnWorld.spawnPos
                    player.networkHandler.sendPacket(
                        GameStateChangeS2CPacket(
                            GameStateChangeS2CPacket.NO_RESPAWN_BLOCK,
                            0.0f
                        )
                    )
                } else {
                    blockPos = respawnWorld.spawnPos
                }

                var box = player.getDimensions(EntityPose.STANDING).getBoxAt(blockPos?.toVec3d())

                while (!respawnWorld.isSpaceEmpty(box) && box.minY < respawnWorld.topY) {
                    blockPos = blockPos?.up()
                    box = box.offset(0.0, 1.0, 0.0)
                }

                if (blockPos != null) {
                    player.teleport(
                        respawnWorld,
                        blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble(), angle, 0.0F
                    )
                }
            }

            player.sendMessage(
                Text.translatableWithFallback("cobbleblackout.blackoutmessage", "You blacked out!"),
                true
            )

            //Wait for post-battle updates to finish
            afterOnServer(seconds = 0.5F) {
                player.party().heal()
            }
        }
    }
}
