package za.co.neroland.nerologistics.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerologistics.NeroLogisticsCommon;

/** MinecraftForge entry point for NeroLogistics. */
@Mod(NeroLogisticsCommon.MOD_ID)
public final class NeroLogisticsForge {

    public NeroLogisticsForge(FMLJavaModLoadingContext context) {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Forge bootstrap");
        NeroLogisticsCommon.init();
    }
}
