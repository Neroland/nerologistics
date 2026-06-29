package za.co.neroland.nerologistics.transport;

import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral item-handle contract — NeroLogistics' slot-addressed view of any
 * inventory its ducts route through. Core ships {@code NeroEnergyStorage} and
 * {@code NeroFluidStorage} as neutral energy/fluid surfaces, but has no item
 * equivalent, so NeroLogistics owns this one. Each loader bridges it to its native
 * inventory mechanism (NeoForge {@code IItemHandler}, Fabric
 * {@code Storage<ItemVariant>}, Forge {@code IItemHandler}) behind {@link
 * za.co.neroland.nerologistics.platform.IItemLookup}, so the routing graph speaks one
 * vocabulary regardless of who exposed the inventory (vanilla chest, Nerotech output,
 * AE2 interface, Mekanism pipe).
 *
 * <p>Amounts are item counts; positions are slot indices. All mutating calls accept a
 * {@code simulate} flag so the transport scheduler can plan a move before committing it.
 * The item analogue of {@link za.co.neroland.nerolandcore.energy.NeroEnergyStorage}.
 */
public interface ItemPort {

    /** Number of addressable slots exposed on this face. */
    int size();

    /** The stack in {@code slot} (a copy or read-only view; never mutate it directly). */
    ItemStack get(int slot);

    /**
     * Insert into {@code slot}.
     *
     * @return the remainder that could not be inserted ({@link ItemStack#EMPTY} if all fit).
     */
    ItemStack insert(int slot, ItemStack stack, boolean simulate);

    /**
     * Extract up to {@code amount} from {@code slot}.
     *
     * @return the extracted stack ({@link ItemStack#EMPTY} if nothing could be taken).
     */
    ItemStack extract(int slot, int amount, boolean simulate);
}
