package za.co.neroland.nerologistics.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.client.AutoCrafterScreen;
import za.co.neroland.nerologistics.client.BufferScreen;
import za.co.neroland.nerologistics.client.DeliveryDroneRenderer;
import za.co.neroland.nerologistics.client.FilterScreen;
import za.co.neroland.nerologistics.client.StorageRequestScreen;
import za.co.neroland.nerologistics.registry.ModEntities;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/** Fabric client entry point for NeroLogistics — registers screens and entity renderers. */
public final class NeroLogisticsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Fabric client bootstrap");
        MenuScreens.register(ModMenuTypes.STORAGE_REQUEST.get(), StorageRequestScreen::new);
        MenuScreens.register(ModMenuTypes.FILTER.get(), FilterScreen::new);
        MenuScreens.register(ModMenuTypes.AUTO_CRAFTER.get(), AutoCrafterScreen::new);
        MenuScreens.register(ModMenuTypes.BUFFER.get(), BufferScreen::new);
        EntityRendererRegistry.register(ModEntities.DELIVERY_DRONE.get(), DeliveryDroneRenderer::new);
    }
}
