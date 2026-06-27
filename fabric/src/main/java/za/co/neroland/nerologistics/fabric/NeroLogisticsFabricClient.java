package za.co.neroland.nerologistics.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.nerologistics.NeroLogisticsCommon;

/** Fabric client entry point for NeroLogistics. */
public final class NeroLogisticsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Fabric client bootstrap");
    }
}
