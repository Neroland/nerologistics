package za.co.neroland.nerologistics.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.registry.NeoForgeRegistrationFactory;

/** NeoForge entry point for NeroLogistics. */
@Mod(NeroLogisticsCommon.MOD_ID)
public final class NeroLogisticsNeoForge {

    public NeroLogisticsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] NeoForge bootstrap");
        // Shared init builds the DeferredRegisters via the RegistrationProvider seam;
        // attach them to NeroLogistics' mod event bus.
        NeroLogisticsCommon.init();
        NeoForgeRegistrationFactory.registerAll(modEventBus);
    }
}
