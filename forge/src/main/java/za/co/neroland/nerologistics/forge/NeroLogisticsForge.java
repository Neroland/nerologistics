package za.co.neroland.nerologistics.forge;

import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.registry.ForgeRegistrationFactory;

/** MinecraftForge entry point for NeroLogistics. */
@Mod(NeroLogisticsCommon.MOD_ID)
public final class NeroLogisticsForge {

    public NeroLogisticsForge(FMLJavaModLoadingContext context) {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Forge bootstrap");
        BusGroup modBusGroup = context.getModBusGroup();
        // Shared init builds the DeferredRegisters via the RegistrationProvider seam;
        // attach them to NeroLogistics' mod bus group.
        NeroLogisticsCommon.init();
        ForgeRegistrationFactory.registerAll(modBusGroup);
    }
}
