package com.sylvonir.cobbleblackout

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.scheduling.afterOnServer
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.util.party

object Blackout {
    const val MOD_ID = "cobbleblackout";

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
            val player = it.entity ?: return
            val networkHandler = player.networkHandler
            networkHandler.player = player.getServer()?.playerManager?.respawnPlayer(player, true) ?: return
            afterOnServer(seconds = 1F) {
                networkHandler.player.party().heal()
            }
        }
    }
}
