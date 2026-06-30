package za.co.neroland.nerologistics.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

/**
 * Loader-neutral item movement over vanilla {@link Container} / {@link WorldlyContainer}.
 * Covers vanilla inventories and every Nero machine (they implement {@code WorldlyContainer}
 * through Core). Discovery of loader-native transfer-API or AE2 inventories via the Stage-1
 * {@code IItemLookup} seam is a Stage-3 enhancement; Stage-2 routing uses the vanilla contract,
 * which keeps this entirely in {@code common} with no loader imports.
 */
public final class InventoryTransfer {

    private InventoryTransfer() {
    }

    /** The {@link Container} at {@code pos}, or {@code null} if the block there is not one. */
    @Nullable
    public static Container containerAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container container ? container : null;
    }

    private static int[] slotsForFace(Container container, Direction side) {
        if (container instanceof WorldlyContainer worldly) {
            return worldly.getSlotsForFace(side);
        }
        int[] all = new int[container.getContainerSize()];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        return all;
    }

    private static boolean canTake(Container container, int slot, ItemStack stack, Direction side) {
        if (container instanceof WorldlyContainer worldly) {
            return worldly.canTakeItemThroughFace(slot, stack, side);
        }
        return true;
    }

    private static boolean canPlace(Container container, int slot, ItemStack stack, Direction side) {
        if (!container.canPlaceItem(slot, stack)) {
            return false;
        }
        if (container instanceof WorldlyContainer worldly) {
            return worldly.canPlaceItemThroughFace(slot, stack, side);
        }
        return true;
    }

    /**
     * Insert up to {@code amount} of {@code prototype} into {@code dest} (entered on its
     * {@code side}). Mutates the container. {@code prototype} is not modified.
     *
     * @return the number of items actually inserted.
     */
    public static int insert(Container dest, Direction side, ItemStack prototype, int amount) {
        int remaining = Math.min(amount, prototype.getMaxStackSize());
        int inserted = 0;
        // First merge into existing matching stacks, then fill empty slots.
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            boolean emptyPass = pass == 1;
            for (int slot : slotsForFace(dest, side)) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack inSlot = dest.getItem(slot);
                if (emptyPass) {
                    if (!inSlot.isEmpty() || !canPlace(dest, slot, prototype, side)) {
                        continue;
                    }
                    int room = Math.min(remaining, Math.min(prototype.getMaxStackSize(), dest.getMaxStackSize()));
                    ItemStack placed = prototype.copyWithCount(room);
                    dest.setItem(slot, placed);
                    remaining -= room;
                    inserted += room;
                } else {
                    if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, prototype)
                            || !canPlace(dest, slot, prototype, side)) {
                        continue;
                    }
                    int cap = Math.min(inSlot.getMaxStackSize(), dest.getMaxStackSize());
                    int room = Math.min(remaining, cap - inSlot.getCount());
                    if (room <= 0) {
                        continue;
                    }
                    inSlot.grow(room);
                    remaining -= room;
                    inserted += room;
                }
            }
        }
        if (inserted > 0) {
            dest.setChanged();
        }
        return inserted;
    }

    /** Total count of items matching {@code match} (by item type) extractable from {@code source}'s {@code side}. */
    public static int count(Container source, Direction side, ItemStack match) {
        int total = 0;
        for (int slot : slotsForFace(source, side)) {
            ItemStack stack = source.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, match) && canTake(source, slot, stack, side)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Extract up to {@code amount} of items matching {@code match} (by item type) from {@code source}'s
     * {@code side}. Mutates the container.
     *
     * @return the number of items actually extracted.
     */
    public static int extract(Container source, Direction side, ItemStack match, int amount) {
        int got = 0;
        for (int slot : slotsForFace(source, side)) {
            if (got >= amount) {
                break;
            }
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty() || !ItemStack.isSameItem(stack, match) || !canTake(source, slot, stack, side)) {
                continue;
            }
            int take = Math.min(amount - got, stack.getCount());
            stack.shrink(take);
            got += take;
        }
        if (got > 0) {
            source.setChanged();
        }
        return got;
    }

    /** A slot the given face may extract from, holding a non-empty stack — or -1 if none. */
    public static int firstExtractableSlot(Container source, Direction side, int afterSlot) {
        int[] slots = slotsForFace(source, side);
        for (int slot : slots) {
            if (slot <= afterSlot) {
                continue;
            }
            ItemStack stack = source.getItem(slot);
            if (!stack.isEmpty() && canTake(source, slot, stack, side)) {
                return slot;
            }
        }
        return -1;
    }
}
