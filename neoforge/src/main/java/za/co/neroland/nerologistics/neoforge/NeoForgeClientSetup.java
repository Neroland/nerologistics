package za.co.neroland.nerologistics.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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

/** NeoForge client-only wiring (screen + entity renderer registration). Loaded only behind Dist.CLIENT. */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeClientSetup::onRegisterScreens);
        modEventBus.addListener(NeoForgeClientSetup::onRegisterRenderers);
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.STORAGE_REQUEST.get(), StorageRequestScreen::new);
        event.register(ModMenuTypes.FILTER.get(), FilterScreen::new);
        event.register(ModMenuTypes.AUTO_CRAFTER.get(), AutoCrafterScreen::new);
        event.register(ModMenuTypes.BUFFER.get(), BufferScreen::new);
        event.register(ModMenuTypes.DRONE_PORT.get(), DronePortScreen::new);
        event.register(ModMenuTypes.DRIVE_BAY.get(), DriveBayScreen::new);
        event.register(ModMenuTypes.CELL_CONFIG.get(), CellConfigScreen::new);
        event.register(ModMenuTypes.STORAGE_TERMINAL.get(), StorageTerminalScreen::new);
        event.register(ModMenuTypes.LOGISTICS_PROCESSOR.get(), LogisticsProcessorScreen::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DELIVERY_DRONE.get(), DeliveryDroneRenderer::new);
    }
}
