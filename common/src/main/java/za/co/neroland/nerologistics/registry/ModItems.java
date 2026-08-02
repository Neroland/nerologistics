package za.co.neroland.nerologistics.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.item.ConfiguratorItem;
import za.co.neroland.nerologistics.item.WirelessTerminalItem;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.nerologistics.storage.StorageCellItem;

/** NeroLogistics block-items, registered cross-loader via {@link RegistrationProvider}. */
public final class ModItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroLogisticsCommon.MOD_ID);

    // --- Stage 7: network controller + module -------------------------------
    public static final RegistryEntry<BlockItem> NETWORK_CONTROLLER_ITEM =
            blockItem("network_controller", ModBlocks.NETWORK_CONTROLLER);
    public static final RegistryEntry<BlockItem> NETWORK_MODULE_ITEM =
            blockItem("network_module", ModBlocks.NETWORK_MODULE);

    // --- Stage 8: universal duct + native storage ---------------------------
    public static final RegistryEntry<BlockItem> UNIVERSAL_DUCT_ITEM =
            blockItem("universal_duct", ModBlocks.UNIVERSAL_DUCT);
    public static final RegistryEntry<BlockItem> ITEM_STORAGE_ITEM =
            blockItem("item_storage", ModBlocks.ITEM_STORAGE);
    public static final RegistryEntry<BlockItem> AUTO_CRAFTER_ITEM =
            blockItem("auto_crafter", ModBlocks.AUTO_CRAFTER);
    public static final RegistryEntry<BlockItem> BUFFER_ITEM =
            blockItem("buffer", ModBlocks.BUFFER);

    // --- Stage 14: digital storage network ----------------------------------
    public static final RegistryEntry<BlockItem> DRIVE_BAY_ITEM =
            blockItem("drive_bay", ModBlocks.DRIVE_BAY);
    public static final RegistryEntry<StorageCellItem> ITEM_CELL_1K =
            cell("item_cell_1k", StorageCellItem.Kind.ITEM, 0);
    public static final RegistryEntry<StorageCellItem> ITEM_CELL_8K =
            cell("item_cell_8k", StorageCellItem.Kind.ITEM, 1);
    public static final RegistryEntry<StorageCellItem> ITEM_CELL_64K =
            cell("item_cell_64k", StorageCellItem.Kind.ITEM, 2);
    public static final RegistryEntry<StorageCellItem> ITEM_CELL_512K =
            cell("item_cell_512k", StorageCellItem.Kind.ITEM, 3);
    public static final RegistryEntry<StorageCellItem> FLUID_CELL_16B =
            cell("fluid_cell_16b", StorageCellItem.Kind.FLUID, 0);
    public static final RegistryEntry<StorageCellItem> FLUID_CELL_128B =
            cell("fluid_cell_128b", StorageCellItem.Kind.FLUID, 1);
    public static final RegistryEntry<StorageCellItem> FLUID_CELL_1024B =
            cell("fluid_cell_1024b", StorageCellItem.Kind.FLUID, 2);
    public static final RegistryEntry<StorageCellItem> FLUID_CELL_8192B =
            cell("fluid_cell_8192b", StorageCellItem.Kind.FLUID, 3);

    // --- Stage 16: logistics processor ---------------------------------------
    public static final RegistryEntry<BlockItem> LOGISTICS_PROCESSOR_ITEM =
            blockItem("logistics_processor", ModBlocks.LOGISTICS_PROCESSOR);

    // --- Stage 15: storage terminal + wireless terminal ---------------------
    public static final RegistryEntry<BlockItem> STORAGE_TERMINAL_ITEM =
            blockItem("storage_terminal", ModBlocks.STORAGE_TERMINAL);
    public static final RegistryEntry<WirelessTerminalItem> WIRELESS_TERMINAL =
            ITEMS.register("wireless_terminal",
                    key -> new WirelessTerminalItem(new Item.Properties().setId(key).stacksTo(1)));

    // --- Stage 11: drone port + drones + upgrade cards ----------------------
    public static final RegistryEntry<BlockItem> DRONE_PORT_ITEM =
            blockItem("drone_port", ModBlocks.DRONE_PORT);
    public static final RegistryEntry<Item> DRONE =
            ITEMS.register("drone", key -> new Item(new Item.Properties().setId(key)));
    public static final RegistryEntry<Item> HYPERSPEED_CARD =
            ITEMS.register("hyperspeed_card", key -> new Item(new Item.Properties().setId(key)));

    // --- Stage 12: native trains --------------------------------------------
    public static final RegistryEntry<BlockItem> TRAIN_STATION_ITEM =
            blockItem("train_station", ModBlocks.TRAIN_STATION);

    public static final RegistryEntry<BlockItem> ITEM_DUCT_ITEM = blockItem("item_duct", ModBlocks.ITEM_DUCT);
    public static final RegistryEntry<BlockItem> FLUID_DUCT_ITEM = blockItem("fluid_duct", ModBlocks.FLUID_DUCT);
    public static final RegistryEntry<BlockItem> ENERGY_CABLE_ITEM = blockItem("energy_cable", ModBlocks.ENERGY_CABLE);

    // --- Stage 3 ------------------------------------------------------------
    public static final RegistryEntry<BlockItem> WIRELESS_CARGO_TERMINAL_ITEM =
            blockItem("wireless_cargo_terminal", ModBlocks.WIRELESS_CARGO_TERMINAL);
    public static final RegistryEntry<BlockItem> STORAGE_REQUEST_TERMINAL_ITEM =
            blockItem("storage_request_terminal", ModBlocks.STORAGE_REQUEST_TERMINAL);
    public static final RegistryEntry<BlockItem> TRAIN_CARGO_INTERFACE_ITEM =
            blockItem("train_cargo_interface", ModBlocks.TRAIN_CARGO_INTERFACE);
    public static final RegistryEntry<BlockItem> DRONE_HUB_ITEM =
            blockItem("drone_hub", ModBlocks.DRONE_HUB);

    // --- Stage 4 ------------------------------------------------------------
    public static final RegistryEntry<BlockItem> ROCKET_CARGO_PORT_ITEM =
            blockItem("rocket_cargo_port", ModBlocks.ROCKET_CARGO_PORT);

    // --- Stage 5 ------------------------------------------------------------
    public static final RegistryEntry<BlockItem> LOGISTICS_DASHBOARD_ITEM =
            blockItem("logistics_dashboard", ModBlocks.LOGISTICS_DASHBOARD);

    // --- Tools --------------------------------------------------------------
    public static final RegistryEntry<Item> CONFIGURATOR =
            ITEMS.register("configurator", key -> new ConfiguratorItem(new Item.Properties().setId(key)));

    private static List<RegistryEntry<? extends ItemLike>> creativeOrder() {
        return List.of(NETWORK_CONTROLLER_ITEM, NETWORK_MODULE_ITEM,
                UNIVERSAL_DUCT_ITEM, ITEM_STORAGE_ITEM, AUTO_CRAFTER_ITEM, BUFFER_ITEM,
                LOGISTICS_PROCESSOR_ITEM,
                DRIVE_BAY_ITEM, ITEM_CELL_1K, ITEM_CELL_8K, ITEM_CELL_64K, ITEM_CELL_512K,
                FLUID_CELL_16B, FLUID_CELL_128B, FLUID_CELL_1024B, FLUID_CELL_8192B,
                STORAGE_TERMINAL_ITEM, WIRELESS_TERMINAL,
                ITEM_DUCT_ITEM, FLUID_DUCT_ITEM, ENERGY_CABLE_ITEM,
                WIRELESS_CARGO_TERMINAL_ITEM, STORAGE_REQUEST_TERMINAL_ITEM,
                TRAIN_CARGO_INTERFACE_ITEM, TRAIN_STATION_ITEM, DRONE_HUB_ITEM, ROCKET_CARGO_PORT_ITEM,
                DRONE_PORT_ITEM, DRONE, HYPERSPEED_CARD,
                LOGISTICS_DASHBOARD_ITEM, CONFIGURATOR);
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }

    /** A storage cell: never stacks (its contents live on the individual stack's components). */
    private static RegistryEntry<StorageCellItem> cell(String name, StorageCellItem.Kind kind, int tier) {
        return ITEMS.register(name,
                key -> new StorageCellItem(new Item.Properties().setId(key).stacksTo(1), kind, tier));
    }

    /** Every NeroLogistics item as {@link ItemLike}, in display order — drained into the creative tab. */
    public static List<ItemLike> creativeContents() {
        List<ItemLike> out = new ArrayList<>();
        for (RegistryEntry<? extends ItemLike> entry : creativeOrder()) {
            out.add(entry.get());
        }
        return out;
    }

    private ModItems() {
    }

    public static void init() {
    }
}
