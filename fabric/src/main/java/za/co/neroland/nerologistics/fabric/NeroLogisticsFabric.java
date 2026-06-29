package za.co.neroland.nerologistics.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;

import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.platform.FabricEnergyLookup;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.AbstractTerminalBlockEntity;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.ship.ShipmentManager;

/** Fabric entry point for NeroLogistics. Registration is eager; capabilities are wired here. */
public final class NeroLogisticsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] Fabric bootstrap");
        NeroLogisticsCommon.init();

        // Energy: terminals/hub/port accept NE from cables on Core's shared energy lookup.
        energy(ModBlockEntities.WIRELESS_CARGO_TERMINAL.get());
        energy(ModBlockEntities.DRONE_HUB.get());
        energy(ModBlockEntities.ROCKET_CARGO_PORT.get());

        // Items: expose terminal/interface buffers on the Fabric Transfer API so hoppers, Create and
        // other mods move items in/out (NeroLogistics' own ducts use the vanilla Container directly).
        item(ModBlockEntities.WIRELESS_CARGO_TERMINAL.get());
        item(ModBlockEntities.STORAGE_REQUEST_TERMINAL.get());
        item(ModBlockEntities.TRAIN_CARGO_INTERFACE.get());
        item(ModBlockEntities.DRONE_HUB.get());
        item(ModBlockEntities.ROCKET_CARGO_PORT.get());

        // Drive cross-dimension shipment arrivals once per server tick.
        ServerTickEvents.END_SERVER_TICK.register(ShipmentManager::tick);
    }

    private static <T extends AbstractTerminalBlockEntity> void energy(BlockEntityType<T> type) {
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.getEnergy(), type);
    }

    private static <T extends AbstractTerminalBlockEntity> void item(BlockEntityType<T> type) {
        ItemStorage.SIDED.registerForBlockEntity((be, dir) -> ContainerStorage.of(be, dir), type);
    }
}
