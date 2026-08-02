package za.co.neroland.nerologistics.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.conduit.ItemDuctBlockEntity;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/**
 * Menu for an item duct's whitelist filter. The top row is "ghost" filter slots: <b>shift-click an item
 * in your inventory</b> to stamp a count-1 copy as a filter rule (your item is not consumed), and
 * <b>shift-click a filter slot</b> to clear it. The filter slots can't be picked up or dropped into
 * directly, so the stamps stay virtual (nothing to lose on break).
 *
 * <p>The server-side menu holds the owning duct block entity so every filter edit is persisted
 * ({@code setChanged()}) and the menu closes when the duct is broken or the player walks away
 * ({@link #stillValid}); the client-side menu (2-arg constructor) has neither and mirrors state via
 * normal slot sync.
 */
public class FilterMenu extends AbstractContainerMenu {

    private static final int FILTER = ItemDuctBlockEntity.FILTER_SLOTS;

    private final Container filter;
    /** Owning duct on the server; {@code null} on the client (and for the display-only fallback). */
    @Nullable
    private final BlockEntity blockEntity;

    public FilterMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(FILTER), null);
    }

    public FilterMenu(int id, Inventory playerInventory, Container filter, @Nullable BlockEntity blockEntity) {
        super(ModMenuTypes.FILTER.get(), id);
        this.filter = filter;
        this.blockEntity = blockEntity;
        for (int col = 0; col < FILTER; col++) {
            this.addSlot(new FilterSlot(filter, col, 8 + col * 18, 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity == null) {
            return true; // client mirror — the server-side menu is authoritative
        }
        if (this.blockEntity.isRemoved() || this.blockEntity.getLevel() == null
                || this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos()) != this.blockEntity) {
            return false;
        }
        return player.distanceToSqr(this.blockEntity.getBlockPos().getX() + 0.5D,
                this.blockEntity.getBlockPos().getY() + 0.5D,
                this.blockEntity.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    /**
     * Ghost-filter handling via shift-click: shift-clicking a filter slot clears it; shift-clicking a
     * player item stamps a count-1 copy into the first empty filter slot. The player's item is never
     * moved, so {@link ItemStack#EMPTY} is always returned. Every edit is persisted to the owning duct.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < FILTER) {
            this.filter.setItem(index, ItemStack.EMPTY);
            filterChanged();
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            for (int i = 0; i < FILTER; i++) {
                if (this.filter.getItem(i).isEmpty()) {
                    this.filter.setItem(i, stack.copyWithCount(1));
                    filterChanged();
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Persist a filter edit — without this the whitelist silently reverts on chunk unload. */
    private void filterChanged() {
        if (this.blockEntity != null) {
            this.blockEntity.setChanged();
        }
    }

    /** Display-only filter slot: never picked up or placed into directly; set via shift-click. */
    private static final class FilterSlot extends Slot {
        FilterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
