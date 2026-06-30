package za.co.neroland.nerologistics.registry;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.DroneHubBlockEntity;
import za.co.neroland.nerologistics.conduit.EnergyCableBlockEntity;
import za.co.neroland.nerologistics.conduit.FluidDuctBlockEntity;
import za.co.neroland.nerologistics.conduit.ItemDuctBlockEntity;
import za.co.neroland.nerologistics.conduit.NetworkControllerBlockEntity;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;
import za.co.neroland.nerologistics.conduit.StorageRequestTerminalBlockEntity;
import za.co.neroland.nerologistics.conduit.TrainCargoInterfaceBlockEntity;
import za.co.neroland.nerologistics.conduit.WirelessCargoTerminalBlockEntity;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;

/** Block-entity types for NeroLogistics conduits, registered cross-loader via {@link RegistrationProvider}. */
public final class ModBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroLogisticsCommon.MOD_ID);

    public static final RegistryEntry<BlockEntityType<NetworkControllerBlockEntity>> NETWORK_CONTROLLER =
            BLOCK_ENTITIES.register("network_controller",
                    key -> new BlockEntityType<>(NetworkControllerBlockEntity::new,
                            Set.of(ModBlocks.NETWORK_CONTROLLER.get())));

    public static final RegistryEntry<BlockEntityType<ItemDuctBlockEntity>> ITEM_DUCT =
            BLOCK_ENTITIES.register("item_duct",
                    key -> new BlockEntityType<>(ItemDuctBlockEntity::new, Set.of(ModBlocks.ITEM_DUCT.get())));

    public static final RegistryEntry<BlockEntityType<FluidDuctBlockEntity>> FLUID_DUCT =
            BLOCK_ENTITIES.register("fluid_duct",
                    key -> new BlockEntityType<>(FluidDuctBlockEntity::new, Set.of(ModBlocks.FLUID_DUCT.get())));

    public static final RegistryEntry<BlockEntityType<EnergyCableBlockEntity>> ENERGY_CABLE =
            BLOCK_ENTITIES.register("energy_cable",
                    key -> new BlockEntityType<>(EnergyCableBlockEntity::new, Set.of(ModBlocks.ENERGY_CABLE.get())));

    public static final RegistryEntry<BlockEntityType<WirelessCargoTerminalBlockEntity>> WIRELESS_CARGO_TERMINAL =
            BLOCK_ENTITIES.register("wireless_cargo_terminal",
                    key -> new BlockEntityType<>(WirelessCargoTerminalBlockEntity::new,
                            Set.of(ModBlocks.WIRELESS_CARGO_TERMINAL.get())));

    public static final RegistryEntry<BlockEntityType<StorageRequestTerminalBlockEntity>> STORAGE_REQUEST_TERMINAL =
            BLOCK_ENTITIES.register("storage_request_terminal",
                    key -> new BlockEntityType<>(StorageRequestTerminalBlockEntity::new,
                            Set.of(ModBlocks.STORAGE_REQUEST_TERMINAL.get())));

    public static final RegistryEntry<BlockEntityType<TrainCargoInterfaceBlockEntity>> TRAIN_CARGO_INTERFACE =
            BLOCK_ENTITIES.register("train_cargo_interface",
                    key -> new BlockEntityType<>(TrainCargoInterfaceBlockEntity::new,
                            Set.of(ModBlocks.TRAIN_CARGO_INTERFACE.get())));

    public static final RegistryEntry<BlockEntityType<DroneHubBlockEntity>> DRONE_HUB =
            BLOCK_ENTITIES.register("drone_hub",
                    key -> new BlockEntityType<>(DroneHubBlockEntity::new, Set.of(ModBlocks.DRONE_HUB.get())));

    public static final RegistryEntry<BlockEntityType<RocketCargoPortBlockEntity>> ROCKET_CARGO_PORT =
            BLOCK_ENTITIES.register("rocket_cargo_port",
                    key -> new BlockEntityType<>(RocketCargoPortBlockEntity::new,
                            Set.of(ModBlocks.ROCKET_CARGO_PORT.get())));

    private ModBlockEntities() {
    }

    public static void init() {
    }
}
