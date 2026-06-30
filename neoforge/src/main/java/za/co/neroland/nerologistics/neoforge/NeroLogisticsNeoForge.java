package za.co.neroland.nerologistics.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.platform.NeoForgeEnergyLookup;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.command.NeroLogisticsCommands;
import za.co.neroland.nerologistics.conduit.AbstractTerminalBlockEntity;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.registry.NeoForgeRegistrationFactory;
import za.co.neroland.nerologistics.ship.ShipmentManager;
import za.co.neroland.nerologistics.telemetry.NeroLogisticsTelemetry;

/** NeoForge entry point for NeroLogistics. */
@Mod(NeroLogisticsCommon.MOD_ID)
public final class NeroLogisticsNeoForge {

    public NeroLogisticsNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] NeoForge bootstrap");
        // Shared init builds the DeferredRegisters via the RegistrationProvider seam;
        // attach them to NeroLogistics' mod event bus.
        NeroLogisticsCommon.init();
        NeoForgeRegistrationFactory.registerAll(modEventBus);
        // Anonymous, NeroLogistics-only crash reporting (opt-out via config; off in dev unless DSN set).
        NeroLogisticsTelemetry.init();
        modEventBus.addListener(NeroLogisticsNeoForge::onRegisterCapabilities);
        // Drive cross-dimension shipment arrivals once per server tick.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ShipmentManager.tick(event.getServer()));
        // /nerologistics gallery
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                NeroLogisticsCommands.register(event.getDispatcher()));
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClientSetup.init(modEventBus);
        }
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // Energy: cables power the network controller, wireless terminal + drone hub via Core's shared cap.
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, ModBlockEntities.NETWORK_CONTROLLER.get(),
                (be, side) -> be.getEnergy());
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, ModBlockEntities.AUTO_CRAFTER.get(),
                (be, side) -> be.getEnergy());
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, ModBlockEntities.WIRELESS_CARGO_TERMINAL.get(),
                (be, side) -> be.getEnergy());
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, ModBlockEntities.DRONE_HUB.get(),
                (be, side) -> be.getEnergy());
        event.registerBlockEntity(NeoForgeEnergyLookup.ENERGY, ModBlockEntities.ROCKET_CARGO_PORT.get(),
                (be, side) -> be.getEnergy());

        // Items: terminal/interface/storage buffers on the standard item capability for hoppers, Create, AE2, etc.
        itemCap(event, ModBlockEntities.ITEM_STORAGE.get());
        itemCap(event, ModBlockEntities.AUTO_CRAFTER.get());
        itemCap(event, ModBlockEntities.BUFFER.get());
        itemCap(event, ModBlockEntities.WIRELESS_CARGO_TERMINAL.get());
        itemCap(event, ModBlockEntities.STORAGE_REQUEST_TERMINAL.get());
        itemCap(event, ModBlockEntities.TRAIN_CARGO_INTERFACE.get());
        itemCap(event, ModBlockEntities.DRONE_HUB.get());
        itemCap(event, ModBlockEntities.ROCKET_CARGO_PORT.get());
    }

    private static <T extends AbstractTerminalBlockEntity> void itemCap(RegisterCapabilitiesEvent event,
            BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, type,
                (be, side) -> side != null ? new WorldlyContainerWrapper(be, side) : VanillaContainerWrapper.of(be));
    }
}
