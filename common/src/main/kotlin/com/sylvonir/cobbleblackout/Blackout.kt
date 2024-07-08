package com.sylvonir.cobbleblackout

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.toBlockPos
import net.minecraft.block.Blocks
import net.minecraft.entity.EntityPose
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
     * Mostly matches vanilla respawn behavior.
     * Except always spawn at world spawn center
     * instead of obeying spawnRadius gamerule,
     * because the vanilla function for calculating it is not easily accessible,
     * and I'm lazy.
     */
    private fun calcRespawnPosAndTeleport(player: ServerPlayerEntity) {
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
            if (optional.isPresent) { // If valid bed or anchor
                val blockState = respawnWorld.getBlockState(blockPos)
                if (blockState != null && (blockState.isIn(BlockTags.BEDS) || blockState.isOf(Blocks.RESPAWN_ANCHOR))) {
                    val vec3d = Vec3d.ofBottomCenter(blockPos).subtract(optional.get()).normalize()
                    angle = MathHelper.wrapDegrees(MathHelper.atan2(vec3d.z, vec3d.x) * 57.2957763671875 - 90.0)
                        .toFloat()
                }
                blockPos = optional.get().toBlockPos()
            } else if (blockPos != null) { // If invalid bed or anchor
                blockPos = respawnWorld.spawnPos
                player.networkHandler.sendPacket(
                    GameStateChangeS2CPacket(
                        GameStateChangeS2CPacket.NO_RESPAWN_BLOCK,
                        GameStateChangeS2CPacket.DEMO_OPEN_SCREEN.toFloat()
                    )
                )
            } else { // If no bed or anchor
                blockPos = respawnWorld.spawnPos
            }

            var box = player.getDimensions(EntityPose.STANDING).getBoxAt(blockPos?.toCenterPos())

            while (!respawnWorld.isSpaceEmpty(box) && box.minY < respawnWorld.topY) {
                blockPos = blockPos?.up()
                box = box.offset(0.0, 1.0, 0.0)
            }

            val respawnPos = blockPos?.toCenterPos()

            if (respawnPos != null) {
                player.teleport(
                    respawnWorld,
                    respawnPos.x, respawnPos.y, respawnPos.z, angle, 0.0F
                )
            }
        }
    }
}
