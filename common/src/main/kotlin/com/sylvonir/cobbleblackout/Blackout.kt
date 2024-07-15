package com.sylvonir.cobbleblackout

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.util.party
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.network.ServerPlayerEntity
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

            player.addStatusEffect(StatusEffectInstance(StatusEffects.BLINDNESS, 40))
            player.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3))

            calcRespawnPosAndTeleport(player)

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

    /**
     * Should match vanilla respawn behavior.
     */
    private fun calcRespawnPosAndTeleport(player: ServerPlayerEntity) {
        val blockPos = player.spawnPointPosition
        var angle = player.spawnAngle
        val forced = player.isSpawnForced
        var optional = Optional.empty<Vec3d>()
        var respawnWorld = player.getServer()?.getWorld(player.spawnPointDimension)

        if (blockPos != null && respawnWorld != null) {
            optional = PlayerEntity.findRespawnPosition(respawnWorld, blockPos, angle, forced, true)
        } else {
            respawnWorld = player.getServer()?.overworld
        }

        //New players are automatically sent to world spawn position
        val dummy = ServerPlayerEntity(player.server, respawnWorld, player.gameProfile)

        if (respawnWorld != null) {
            if (optional.isPresent) { // If valid bed or anchor
                val respawnPos = optional.get()
                val blockState = respawnWorld.getBlockState(blockPos)
                if (blockState != null && (blockState.isIn(BlockTags.BEDS) || blockState.isOf(Blocks.RESPAWN_ANCHOR))) {
                    val vec3d = Vec3d.ofBottomCenter(blockPos).subtract(respawnPos).normalize()
                    angle = MathHelper.wrapDegrees(MathHelper.atan2(vec3d.z, vec3d.x) * 57.2957763671875 - 90.0)
                        .toFloat()
                }
                dummy.refreshPositionAndAngles(respawnPos.x, respawnPos.y, respawnPos.z, angle, 0.0F)
            } else if (blockPos != null) { // If invalid bed or anchor
                player.networkHandler.sendPacket(
                    GameStateChangeS2CPacket(
                        GameStateChangeS2CPacket.NO_RESPAWN_BLOCK,
                        GameStateChangeS2CPacket.DEMO_OPEN_SCREEN.toFloat()
                    )
                )
            }

            while (!respawnWorld.isSpaceEmpty(dummy) && dummy.y < respawnWorld.topY.toDouble()) {
                dummy.setPosition(dummy.x, dummy.y + 1.0, dummy.z)
            }
            player.teleport(
                respawnWorld,
                dummy.x, dummy.y, dummy.z, dummy.yaw, 0.0F
            )
            dummy.serverWorld.removePlayer(dummy, Entity.RemovalReason.DISCARDED)
        }
    }
}

