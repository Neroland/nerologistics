package za.co.neroland.nerologistics.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import za.co.neroland.nerologistics.client.AutoCrafterScreen;
import za.co.neroland.nerologistics.client.BufferScreen;
import za.co.neroland.nerologistics.client.DeliveryDroneRenderer;
import za.co.neroland.nerologistics.client.FilterScreen;
import za.co.neroland.nerologistics.client.StorageRequestScreen;
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
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DELIVERY_DRONE.get(), DeliveryDroneRenderer::new);
    }
}
