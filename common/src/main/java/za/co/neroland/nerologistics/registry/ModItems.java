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

    private static List<RegistryEntry<? extends ItemLike>> creativeOrder() {
        return List.of(ITEM_DUCT_ITEM, FLUID_DUCT_ITEM, ENERGY_CABLE_ITEM);
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
