package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Item Storage — a native network warehouse block. It is a 54-slot {@code WorldlyContainer} (via
 * {@link AbstractTerminalBlockEntity}), so universal ducts route items into and out of it, hoppers and
 * (on NeoForge/Fabric) the item capability expose it to other mods, and the storage request terminal
 * aggregates its contents into the network index — with no extra wiring, because NeroLogistics discovers
 * inventories through the vanilla {@code Container} contract. Right-click opens a vanilla double-chest
 * GUI (no custom menu/screen needed). The inherited energy buffer is unused — storage is passive.
 *
 * <p>Capacity tiers and typed item/fluid/gas storage <em>cells</em> are a Stage-8 follow-up; this block
 * is the baseline native item store.
 */
public class ItemStorageBlockEntity extends AbstractTerminalBlockEntity implements MenuProvider {

    /** Six rows of nine — matches the vanilla {@link ChestMenu#sixRows} layout. */
    public static final int SLOTS = 54;

    public ItemStorageBlockEntity(BlockPos pos, BlockState state) {
        // No energy use: a tiny buffer keeps the base contract happy without exposing meaningful power.
        super(ModBlockEntities.ITEM_STORAGE.get(), pos, state, SLOTS, 0, 0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.item_storage");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return ChestMenu.sixRows(id, playerInventory, this);
    }
}
