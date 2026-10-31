package za.co.neroland.nerologistics.registry;

import java.util.Set;
import java.util.function.BiFunction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.DroneHubBlockEntity;
import za.co.neroland.nerologistics.conduit.EnergyCableBlockEntity;
import za.co.neroland.nerologistics.conduit.FluidDuctBlockEntity;
import za.co.neroland.nerologistics.conduit.AutoCrafterBlockEntity;
import za.co.neroland.nerologistics.conduit.BufferBlockEntity;
import za.co.neroland.nerologistics.conduit.DronePortBlockEntity;
import za.co.neroland.nerologistics.conduit.TrainStationBlockEntity;
import za.co.neroland.nerologistics.conduit.ItemDuctBlockEntity;
import za.co.neroland.nerologistics.conduit.ItemStorageBlockEntity;
import za.co.neroland.nerologistics.conduit.LogisticsProcessorBlockEntity;
import za.co.neroland.nerologistics.conduit.NetworkControllerBlockEntity;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;
import za.co.neroland.nerologistics.conduit.UniversalDuctBlockEntity;
import za.co.neroland.nerologistics.conduit.StorageRequestTerminalBlockEntity;
import za.co.neroland.nerologistics.conduit.TrainCargoInterfaceBlockEntity;
import za.co.neroland.nerologistics.conduit.WirelessCargoTerminalBlockEntity;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.nerologistics.storage.DriveBayBlockEntity;
import za.co.neroland.nerologistics.storage.StorageTerminalBlockEntity;

/** Block-entity types for NeroLogistics conduits, registered cross-loader via {@link RegistrationProvider}. */
public final class ModBlockEntities {

    public static final RegistrationProvider<BlockEntityType<?>> BLOCK_ENTITIES =
            RegistrationProvider.get(Registries.BLOCK_ENTITY_TYPE, NeroLogisticsCommon.MOD_ID);

    public static final RegistryEntry<BlockEntityType<NetworkControllerBlockEntity>> NETWORK_CONTROLLER =
            BLOCK_ENTITIES.register("network_controller",
                    key -> type(NetworkControllerBlockEntity::new,
                            Set.of(ModBlocks.NETWORK_CONTROLLER.get())));

    public static final RegistryEntry<BlockEntityType<UniversalDuctBlockEntity>> UNIVERSAL_DUCT =
            BLOCK_ENTITIES.register("universal_duct",
                    key -> type(UniversalDuctBlockEntity::new,
                            Set.of(ModBlocks.UNIVERSAL_DUCT.get())));

    public static final RegistryEntry<BlockEntityType<ItemStorageBlockEntity>> ITEM_STORAGE =
            BLOCK_ENTITIES.register("item_storage",
                    key -> type(ItemStorageBlockEntity::new,
                            Set.of(ModBlocks.ITEM_STORAGE.get())));

    public static final RegistryEntry<BlockEntityType<AutoCrafterBlockEntity>> AUTO_CRAFTER =
            BLOCK_ENTITIES.register("auto_crafter",
                    key -> type(AutoCrafterBlockEntity::new,
                            Set.of(ModBlocks.AUTO_CRAFTER.get())));

    public static final RegistryEntry<BlockEntityType<BufferBlockEntity>> BUFFER =
            BLOCK_ENTITIES.register("buffer",
                    key -> type(BufferBlockEntity::new, Set.of(ModBlocks.BUFFER.get())));

    public static final RegistryEntry<BlockEntityType<DronePortBlockEntity>> DRONE_PORT =
            BLOCK_ENTITIES.register("drone_port",
                    key -> type(DronePortBlockEntity::new, Set.of(ModBlocks.DRONE_PORT.get())));

    public static final RegistryEntry<BlockEntityType<TrainStationBlockEntity>> TRAIN_STATION =
            BLOCK_ENTITIES.register("train_station",
                    key -> type(TrainStationBlockEntity::new, Set.of(ModBlocks.TRAIN_STATION.get())));

    public static final RegistryEntry<BlockEntityType<ItemDuctBlockEntity>> ITEM_DUCT =
            BLOCK_ENTITIES.register("item_duct",
                    key -> type(ItemDuctBlockEntity::new, Set.of(ModBlocks.ITEM_DUCT.get())));

    public static final RegistryEntry<BlockEntityType<FluidDuctBlockEntity>> FLUID_DUCT =
            BLOCK_ENTITIES.register("fluid_duct",
                    key -> type(FluidDuctBlockEntity::new, Set.of(ModBlocks.FLUID_DUCT.get())));

    public static final RegistryEntry<BlockEntityType<EnergyCableBlockEntity>> ENERGY_CABLE =
            BLOCK_ENTITIES.register("energy_cable",
                    key -> type(EnergyCableBlockEntity::new, Set.of(ModBlocks.ENERGY_CABLE.get())));

    public static final RegistryEntry<BlockEntityType<WirelessCargoTerminalBlockEntity>> WIRELESS_CARGO_TERMINAL =
            BLOCK_ENTITIES.register("wireless_cargo_terminal",
                    key -> type(WirelessCargoTerminalBlockEntity::new,
                            Set.of(ModBlocks.WIRELESS_CARGO_TERMINAL.get())));

    public static final RegistryEntry<BlockEntityType<StorageRequestTerminalBlockEntity>> STORAGE_REQUEST_TERMINAL =
            BLOCK_ENTITIES.register("storage_request_terminal",
                    key -> type(StorageRequestTerminalBlockEntity::new,
                            Set.of(ModBlocks.STORAGE_REQUEST_TERMINAL.get())));

    public static final RegistryEntry<BlockEntityType<TrainCargoInterfaceBlockEntity>> TRAIN_CARGO_INTERFACE =
            BLOCK_ENTITIES.register("train_cargo_interface",
                    key -> type(TrainCargoInterfaceBlockEntity::new,
                            Set.of(ModBlocks.TRAIN_CARGO_INTERFACE.get())));

    public static final RegistryEntry<BlockEntityType<DroneHubBlockEntity>> DRONE_HUB =
            BLOCK_ENTITIES.register("drone_hub",
                    key -> type(DroneHubBlockEntity::new, Set.of(ModBlocks.DRONE_HUB.get())));

    public static final RegistryEntry<BlockEntityType<RocketCargoPortBlockEntity>> ROCKET_CARGO_PORT =
            BLOCK_ENTITIES.register("rocket_cargo_port",
                    key -> type(RocketCargoPortBlockEntity::new,
                            Set.of(ModBlocks.ROCKET_CARGO_PORT.get())));

    public static final RegistryEntry<BlockEntityType<LogisticsProcessorBlockEntity>> LOGISTICS_PROCESSOR =
            BLOCK_ENTITIES.register("logistics_processor",
                    key -> type(LogisticsProcessorBlockEntity::new,
                            Set.of(ModBlocks.LOGISTICS_PROCESSOR.get())));

    public static final RegistryEntry<BlockEntityType<DriveBayBlockEntity>> DRIVE_BAY =
            BLOCK_ENTITIES.register("drive_bay",
                    key -> type(DriveBayBlockEntity::new, Set.of(ModBlocks.DRIVE_BAY.get())));

    public static final RegistryEntry<BlockEntityType<StorageTerminalBlockEntity>> STORAGE_TERMINAL =
            BLOCK_ENTITIES.register("storage_terminal",
                    key -> type(StorageTerminalBlockEntity::new,
                            Set.of(ModBlocks.STORAGE_TERMINAL.get())));

    /**
     * A block-entity type created by {@code factory}, valid on {@code blocks}. Minecraft 1.21.x builds go
     * through Core's version seam (1.21.1's constructor takes an extra data-fixer argument, and the
     * constructor is private on later 1.21.x lines).
     */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityType<T> type(BiFunction<BlockPos, BlockState, T> factory,
            Set<? extends Block> blocks) {
        //? if >=26.1 {
        return new BlockEntityType<>(factory::apply, (Set<Block>) blocks);
        //?} else {
        /*return za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat.create(factory, blocks.toArray(new Block[0]));
        *///?}
    }

    private ModBlockEntities() {
    }

    public static void init() {
    }
}
