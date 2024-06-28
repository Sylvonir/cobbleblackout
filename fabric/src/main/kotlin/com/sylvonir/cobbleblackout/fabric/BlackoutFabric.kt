package com.sylvonir.cobbleblackout.fabric

import com.sylvonir.cobbleblackout.Blackout
import net.fabricmc.api.ModInitializer

object BlackoutFabric : ModInitializer {
    @Override
    override fun onInitialize() {
        Blackout.init()
    }
}
