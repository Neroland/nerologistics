package za.co.neroland.nerologistics.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.nerologistics.NeroLogisticsCommon;

/** NeoForge entry point for NeroLogistics. */
@Mod(NeroLogisticsCommon.MOD_ID)
public final class NeroLogisticsNeoForge {

    public NeroLogisticsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] NeoForge bootstrap");
        NeroLogisticsCommon.init();
    }
}
