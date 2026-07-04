package za.co.neroland.nerologistics.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerologistics.conduit.ItemDuctBlockEntity;
import za.co.neroland.nerologistics.registry.ModMenuTypes;

/**
 * Menu for an item duct's whitelist filter. The top row is "ghost" filter slots: <b>shift-click an item
 * in your inventory</b> to stamp a count-1 copy as a filter rule (your item is not consumed), and
 * <b>shift-click a filter slot</b> to clear it. The filter slots can't be picked up or dropped into
 * directly, so the stamps stay virtual (nothing to lose on break).
 */
public class FilterMenu extends AbstractContainerMenu {

    private static final int FILTER = ItemDuctBlockEntity.FILTER_SLOTS;

    private final Container filter;

    public FilterMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(FILTER));
    }

    public FilterMenu(int id, Inventory playerInventory, Container filter) {
        super(ModMenuTypes.FILTER.get(), id);
        this.filter = filter;
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
        return this.filter.stillValid(player);
    }

    /**
     * Ghost-filter handling via shift-click: shift-clicking a filter slot clears it; shift-clicking a
     * player item stamps a count-1 copy into the first empty filter slot. The player's item is never
     * moved, so {@link ItemStack#EMPTY} is always returned.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < FILTER) {
            this.filter.setItem(index, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            for (int i = 0; i < FILTER; i++) {
                if (this.filter.getItem(i).isEmpty()) {
                    this.filter.setItem(i, stack.copyWithCount(1));
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
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
