package za.co.neroland.nerologistics.registry;

import java.util.function.Function;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.DroneHubBlock;
import za.co.neroland.nerologistics.conduit.EnergyCableBlock;
import za.co.neroland.nerologistics.conduit.FluidDuctBlock;
import za.co.neroland.nerologistics.conduit.AutoCrafterBlock;
import za.co.neroland.nerologistics.conduit.BufferBlock;
import za.co.neroland.nerologistics.conduit.DronePortBlock;
import za.co.neroland.nerologistics.conduit.ItemDuctBlock;
import za.co.neroland.nerologistics.conduit.ItemStorageBlock;
import za.co.neroland.nerologistics.conduit.NetworkControllerBlock;
import za.co.neroland.nerologistics.conduit.NetworkModuleBlock;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlock;
import za.co.neroland.nerologistics.conduit.UniversalDuctBlock;
import za.co.neroland.nerologistics.conduit.StorageRequestTerminalBlock;
import za.co.neroland.nerologistics.conduit.TrainCargoInterfaceBlock;
import za.co.neroland.nerologistics.conduit.TrainStationBlock;
import za.co.neroland.nerologistics.conduit.WirelessCargoTerminalBlock;
import za.co.neroland.nerologistics.dashboard.LogisticsDashboardBlock;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;

/** NeroLogistics conduit blocks, registered cross-loader via {@link RegistrationProvider}. */
public final class ModBlocks {

    public static final RegistrationProvider<Block> BLOCKS =
            RegistrationProvider.get(Registries.BLOCK, NeroLogisticsCommon.MOD_ID);

    // --- Stage 7: network controller + modular capacity --------------------
    public static final RegistryEntry<NetworkControllerBlock> NETWORK_CONTROLLER =
            register("network_controller", NetworkControllerBlock::new);
    public static final RegistryEntry<NetworkModuleBlock> NETWORK_MODULE =
            register("network_module", NetworkModuleBlock::new);

    // --- Stage 8: universal duct + native storage --------------------------
    public static final RegistryEntry<UniversalDuctBlock> UNIVERSAL_DUCT =
            register("universal_duct", UniversalDuctBlock::new);
    public static final RegistryEntry<ItemStorageBlock> ITEM_STORAGE =
            register("item_storage", ItemStorageBlock::new);

    // --- Stage 9: native auto-crafting -------------------------------------
    public static final RegistryEntry<AutoCrafterBlock> AUTO_CRAFTER =
            register("auto_crafter", AutoCrafterBlock::new);

    // --- Stage 10: buffer block --------------------------------------------
    public static final RegistryEntry<BufferBlock> BUFFER =
            register("buffer", BufferBlock::new);

    // --- Stage 11: drone port ----------------------------------------------
    public static final RegistryEntry<DronePortBlock> DRONE_PORT =
            register("drone_port", DronePortBlock::new);

    // --- Stage 12: native trains -------------------------------------------
    public static final RegistryEntry<TrainStationBlock> TRAIN_STATION =
            register("train_station", TrainStationBlock::new);

    public static final RegistryEntry<ItemDuctBlock> ITEM_DUCT =
            register("item_duct", ItemDuctBlock::new);
    public static final RegistryEntry<FluidDuctBlock> FLUID_DUCT =
            register("fluid_duct", FluidDuctBlock::new);
    public static final RegistryEntry<EnergyCableBlock> ENERGY_CABLE =
            register("energy_cable", EnergyCableBlock::new);

    // --- Stage 3: terminals + drone hub ------------------------------------
    public static final RegistryEntry<WirelessCargoTerminalBlock> WIRELESS_CARGO_TERMINAL =
            register("wireless_cargo_terminal", WirelessCargoTerminalBlock::new);
    public static final RegistryEntry<StorageRequestTerminalBlock> STORAGE_REQUEST_TERMINAL =
            register("storage_request_terminal", StorageRequestTerminalBlock::new);
    public static final RegistryEntry<TrainCargoInterfaceBlock> TRAIN_CARGO_INTERFACE =
            register("train_cargo_interface", TrainCargoInterfaceBlock::new);
    public static final RegistryEntry<DroneHubBlock> DRONE_HUB =
            register("drone_hub", DroneHubBlock::new);

    // --- Stage 4: cross-dimension shipping ---------------------------------
    public static final RegistryEntry<RocketCargoPortBlock> ROCKET_CARGO_PORT =
            register("rocket_cargo_port", RocketCargoPortBlock::new);

    // --- Stage 5: dashboards -----------------------------------------------
    public static final RegistryEntry<LogisticsDashboardBlock> LOGISTICS_DASHBOARD =
            register("logistics_dashboard", LogisticsDashboardBlock::new);

    private static <B extends Block> RegistryEntry<B> register(String name,
            Function<BlockBehaviour.Properties, B> factory) {
        return BLOCKS.register(name, key -> factory.apply(conduitProperties().setId(key)));
    }

    private static BlockBehaviour.Properties conduitProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(1.5F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    private ModBlocks() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}
