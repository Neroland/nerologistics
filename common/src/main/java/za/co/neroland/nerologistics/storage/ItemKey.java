package za.co.neroland.nerologistics.storage;

import net.minecraft.world.item.ItemStack;

/**
 * Hash-map key for "an item type": an exact item + data components pair (count ignored). Wraps a
 * count-1 prototype stack with value-equality semantics ({@link ItemStack#isSameItemSameComponents}
 * / {@link ItemStack#hashItemAndComponents}), which vanilla {@code ItemStack} itself does not
 * provide. Used by the {@link NetworkStorageIndex} to aggregate network contents.
 */
public final class ItemKey {

    private final ItemStack prototype;
    private final int hash;

    private ItemKey(ItemStack prototype) {
        this.prototype = prototype;
        this.hash = ItemStack.hashItemAndComponents(prototype);
    }

    /** Key for {@code stack}'s item type (defensive count-1 copy; {@code stack} is not retained). */
    public static ItemKey of(ItemStack stack) {
        return new ItemKey(stack.copyWithCount(1));
    }

    /** The count-1 prototype this key represents. Callers must not mutate it. */
    public ItemStack prototype() {
        return this.prototype;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof ItemKey other
                && this.hash == other.hash
                && ItemStack.isSameItemSameComponents(this.prototype, other.prototype);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public String toString() {
        return "ItemKey[" + this.prototype + "]";
    }
}
