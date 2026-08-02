package za.co.neroland.nerologistics.storage;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Per-cell configuration stored in the {@code nerologistics:cell_settings} data component (the
 * 26.x replacement for stack NBT): a 9-entry <b>partition</b> (ghost stacks; all-empty = accept
 * anything) and a signed <b>priority</b> (default {@code 0}; higher-priority cells fill first).
 * Immutable — every mutation returns a new value, as data components require.
 *
 * <p>Partition matching is <b>component-sensitive</b> for item cells: a stack matches a partition
 * entry only when {@link ItemStack#isSameItemSameComponents} holds (a plain ghost therefore matches
 * plain stacks only, not damaged/enchanted variants). Fluid cells are partitioned with <b>bucket</b>
 * ghosts: a fluid matches an entry whose ghost item is that fluid's bucket
 * ({@link Fluid#getBucket()}). Network/block-scoped configuration only — no player data.
 */
public record CellSettings(List<ItemStack> partition, int priority) {

    /** Number of partition ghost slots on every cell. */
    public static final int PARTITION_SLOTS = 9;

    /** Fresh cell: unpartitioned, priority 0 (component absent ≡ this). */
    public static final CellSettings DEFAULT = new CellSettings(List.of(), 0);

    public static final Codec<CellSettings> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("partition", List.of())
                    .forGetter(CellSettings::partition),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(CellSettings::priority)
    ).apply(inst, CellSettings::new));

    /** Whether any partition entry is set (an unpartitioned cell accepts everything). */
    public boolean isPartitioned() {
        for (ItemStack ghost : this.partition) {
            if (!ghost.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Count of non-empty partition entries (for tooltips). */
    public int partitionCount() {
        int count = 0;
        for (ItemStack ghost : this.partition) {
            if (!ghost.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Whether an <b>item</b> cell with these settings accepts {@code stack} (component-sensitive). */
    public boolean acceptsItem(ItemStack stack) {
        if (!isPartitioned()) {
            return true;
        }
        for (ItemStack ghost : this.partition) {
            if (!ghost.isEmpty() && ItemStack.isSameItemSameComponents(ghost, stack)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a <b>fluid</b> cell with these settings accepts {@code fluid} (bucket-ghost match). */
    public boolean acceptsFluid(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return false;
        }
        if (!isPartitioned()) {
            return true;
        }
        for (ItemStack ghost : this.partition) {
            if (!ghost.isEmpty() && ghost.getItem() == fluid.getBucket()) {
                return true;
            }
        }
        return false;
    }
}
