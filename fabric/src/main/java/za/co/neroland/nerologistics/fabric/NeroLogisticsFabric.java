package za.co.neroland.nerologistics.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.nerologistics.NeroLogisticsCommon;

/** Fabric entry point for NeroLogistics. */
public final class NeroLogisticsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Fabric bootstrap");
        NeroLogisticsCommon.init();
    }
}
