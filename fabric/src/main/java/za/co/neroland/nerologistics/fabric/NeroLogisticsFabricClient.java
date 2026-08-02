package za.co.neroland.nerologistics.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.client.AutoCrafterScreen;
import za.co.neroland.nerologistics.client.BufferScreen;
import za.co.neroland.nerologistics.client.CellConfigScreen;
import za.co.neroland.nerologistics.client.DeliveryDroneRenderer;
import za.co.neroland.nerologistics.client.DriveBayScreen;
import za.co.neroland.nerologistics.client.DronePortScreen;
import za.co.neroland.nerologistics.client.FilterScreen;
import za.co.neroland.nerologistics.client.LogisticsProcessorScreen;
import za.co.neroland.nerologistics.client.StorageRequestScreen;
import za.co.neroland.nerologistics.client.StorageTerminalScreen;
import za.co.neroland.nerologistics.registry.ModEntities;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/** Fabric client entry point for NeroLogistics — registers screens and entity renderers. */
public final class NeroLogisticsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Fabric client bootstrap");
        // Clientbound receivers for NeroLogistics' own payloads (client-only API).
        FabricNetwork.registerClient();
        MenuScreens.register(ModMenuTypes.STORAGE_REQUEST.get(), StorageRequestScreen::new);
        MenuScreens.register(ModMenuTypes.FILTER.get(), FilterScreen::new);
        MenuScreens.register(ModMenuTypes.AUTO_CRAFTER.get(), AutoCrafterScreen::new);
        MenuScreens.register(ModMenuTypes.BUFFER.get(), BufferScreen::new);
        MenuScreens.register(ModMenuTypes.DRONE_PORT.get(), DronePortScreen::new);
        MenuScreens.register(ModMenuTypes.DRIVE_BAY.get(), DriveBayScreen::new);
        MenuScreens.register(ModMenuTypes.CELL_CONFIG.get(), CellConfigScreen::new);
        MenuScreens.register(ModMenuTypes.STORAGE_TERMINAL.get(), StorageTerminalScreen::new);
        MenuScreens.register(ModMenuTypes.LOGISTICS_PROCESSOR.get(), LogisticsProcessorScreen::new);
        EntityRendererRegistry.register(ModEntities.DELIVERY_DRONE.get(), DeliveryDroneRenderer::new);
    }
}
