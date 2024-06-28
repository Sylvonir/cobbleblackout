package com.sylvonir.cobbleblackout.forge

import com.sylvonir.cobbleblackout.Blackout
import net.minecraftforge.fml.common.Mod

@Mod(Blackout.MOD_ID)
object BlackoutForge {
    const val MOD_ID = "cobbleblackout"

    init {
        Blackout.init()
    }
}
