package za.co.neroland.nerologistics.registry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.registry.RegistrationProvider.RegistryEntry;

/** NeroLogistics block-items, registered cross-loader via {@link RegistrationProvider}. */
public final class ModItems {

    public static final RegistrationProvider<Item> ITEMS =
            RegistrationProvider.get(Registries.ITEM, NeroLogisticsCommon.MOD_ID);

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

    private static List<RegistryEntry<? extends ItemLike>> creativeOrder() {
        return List.of(ITEM_DUCT_ITEM, FLUID_DUCT_ITEM, ENERGY_CABLE_ITEM,
                WIRELESS_CARGO_TERMINAL_ITEM, STORAGE_REQUEST_TERMINAL_ITEM,
                TRAIN_CARGO_INTERFACE_ITEM, DRONE_HUB_ITEM);
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
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
