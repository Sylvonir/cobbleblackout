package com.sylvonir.cobbleblackout

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.util.party
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket
import net.minecraft.text.Text

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
            var player = it.entity ?: return@forEach
            if (player.abilities.creativeMode) return@forEach
            val activeStatusEffects = HashMap(player.activeStatusEffects)

            val networkHandler = player.networkHandler
            networkHandler.player = player.getServer()?.playerManager?.respawnPlayer(player, true) ?: return
            player = networkHandler.player

            //MC-6431 fix, will be unnecessary in 1.21
            player.activeStatusEffects.putAll(activeStatusEffects)
            player.activeStatusEffects.forEach {
                player.networkHandler.sendPacket(EntityStatusEffectS2CPacket(player.id, it.value))
            }

            player.sendMessage(
                Text.translatableWithFallback("cobbleblackout.blackoutmessage", "You blacked out!"),
                true
            )

            player.addStatusEffect(StatusEffectInstance(StatusEffects.BLINDNESS, 40))
            player.addStatusEffect(StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3))

            //Wait for post-battle updates to finish
            afterOnServer(seconds = 0.5F) {
                player.party().heal()
            }
        }
    }
}
