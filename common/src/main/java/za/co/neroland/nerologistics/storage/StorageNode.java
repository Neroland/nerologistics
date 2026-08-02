package za.co.neroland.nerologistics.storage;

import java.util.function.BiConsumer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * A native digital storage participant on a conduit network — implemented by the
 * {@code DriveBayBlockEntity} and discovered by the {@link NetworkStorageIndex} through the
 * network's cached endpoint list (a block entity adjacent to a conduit that implements this
 * interface is indexed as a storage node, <em>not</em> as a vanilla read-through container).
 *
 * <p>Contract:</p>
 * <ul>
 *   <li><b>Versioning</b> — {@link #storageVersion()} must change (monotonically increase)
 *       whenever the node's visible contents change for any reason. The index re-collects a
 *       node's contribution only when its version moves, keeping refreshes O(changed).</li>
 *   <li><b>Priority</b> — {@link #storagePriority()} orders nodes for insertion (higher fills
 *       first). A drive bay reports its best cell priority; exact per-cell ordering is applied
 *       internally by the node.</li>
 *   <li><b>Simulation</b> — {@code simulate = true} must not mutate anything and must return
 *       exactly what the real call would.</li>
 *   <li><b>Prototypes</b> — item matching is exact (item + data components); the prototype stack
 *       is never mutated and its count is ignored.</li>
 * </ul>
 *
 * <p>All state is block/network-scoped — no player data (POPIA/GDPR).</p>
 */
public interface StorageNode {

    /** Whether this node currently participates (config master toggle, redstone, etc.). */
    boolean storageNodeActive();

    /** Insertion ordering across nodes: higher fills first. */
    int storagePriority();

    /** Monotonic content-change counter — bumped on every visible mutation. */
    long storageVersion();

    // --- Items ---------------------------------------------------------------

    /** Insert up to {@code amount} of {@code prototype}. @return items accepted. */
    long insertItem(ItemStack prototype, long amount, boolean simulate);

    /** Extract up to {@code amount} of {@code prototype} (exact match). @return items taken. */
    long extractItem(ItemStack prototype, long amount, boolean simulate);

    /** Report every stored item line as (count-1 prototype, stored count). */
    void collectItems(BiConsumer<ItemStack, Long> out);

    // --- Fluids --------------------------------------------------------------

    /** Fill up to {@code amount} mB of {@code fluid}. @return mB accepted. */
    long insertFluid(Fluid fluid, long amount, boolean simulate);

    /** Drain up to {@code amount} mB of {@code fluid}. @return mB taken. */
    long extractFluid(Fluid fluid, long amount, boolean simulate);

    /** Report every stored fluid line as (fluid, stored mB). */
    void collectFluids(BiConsumer<Fluid, Long> out);
}
