package za.co.neroland.nerologistics.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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

/** Forge client-only wiring (screen + entity renderer registration). */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    public static void init(BusGroup modBusGroup) {
        FMLClientSetupEvent.getBus(modBusGroup).addListener(ForgeClientSetup::onClientSetup);
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ForgeClientSetup::onRegisterEntityRenderers);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ForgeClientSetup::registerScreens);
    }

    private static void registerScreens() {
        MenuScreens.register(ModMenuTypes.STORAGE_REQUEST.get(), StorageRequestScreen::new);
        MenuScreens.register(ModMenuTypes.FILTER.get(), FilterScreen::new);
        MenuScreens.register(ModMenuTypes.AUTO_CRAFTER.get(), AutoCrafterScreen::new);
        MenuScreens.register(ModMenuTypes.BUFFER.get(), BufferScreen::new);
        MenuScreens.register(ModMenuTypes.DRONE_PORT.get(), DronePortScreen::new);
        MenuScreens.register(ModMenuTypes.DRIVE_BAY.get(), DriveBayScreen::new);
        MenuScreens.register(ModMenuTypes.CELL_CONFIG.get(), CellConfigScreen::new);
        MenuScreens.register(ModMenuTypes.STORAGE_TERMINAL.get(), StorageTerminalScreen::new);
        MenuScreens.register(ModMenuTypes.LOGISTICS_PROCESSOR.get(), LogisticsProcessorScreen::new);
    }

    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DELIVERY_DRONE.get(), DeliveryDroneRenderer::new);
    }
}
