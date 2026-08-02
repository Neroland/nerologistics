package za.co.neroland.nerologistics.storage;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;

/**
 * The stored inventory of an <b>item</b> storage cell, kept in the
 * {@code nerologistics:item_cell_contents} data component so a cell is fully portable — pull it
 * from one drive bay and slot it into another and the items travel with it. Entries are keyed by
 * exact item + data components (a plain pickaxe and an enchanted one are two entries); capacity is
 * a pure total item count — no byte/type math, unlimited distinct types within the count.
 * Immutable — every mutation returns a new value, as data components require.
 */
public record ItemCellContents(List<Entry> entries) {

    /** One stored line: a count-1 prototype stack (item + components) and how many are stored. */
    public record Entry(ItemStack item, int count) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                ItemStack.OPTIONAL_CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.INT.fieldOf("count").forGetter(Entry::count)
        ).apply(inst, Entry::new));
    }

    /** Empty cell (component absent ≡ this). */
    public static final ItemCellContents EMPTY = new ItemCellContents(List.of());

    public static final Codec<ItemCellContents> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(ItemCellContents::entries)
    ).apply(inst, ItemCellContents::new));

    /** Total items stored across all entries. */
    public long total() {
        long total = 0;
        for (Entry entry : this.entries) {
            total += entry.count();
        }
        return total;
    }

    /** Number of distinct item types stored. */
    public int typeCount() {
        return this.entries.size();
    }

    /** Count stored of the exact item+components {@code prototype}. */
    public long countOf(ItemStack prototype) {
        for (Entry entry : this.entries) {
            if (ItemStack.isSameItemSameComponents(entry.item(), prototype)) {
                return entry.count();
            }
        }
        return 0;
    }

    /** A copy with {@code amount} more of {@code prototype} (caller enforces capacity). */
    public ItemCellContents grown(ItemStack prototype, int amount) {
        List<Entry> out = new ArrayList<>(this.entries.size() + 1);
        boolean merged = false;
        for (Entry entry : this.entries) {
            if (!merged && ItemStack.isSameItemSameComponents(entry.item(), prototype)) {
                out.add(new Entry(entry.item(), entry.count() + amount));
                merged = true;
            } else {
                out.add(entry);
            }
        }
        if (!merged) {
            out.add(new Entry(prototype.copyWithCount(1), amount));
        }
        return new ItemCellContents(List.copyOf(out));
    }

    /** A copy with up to {@code amount} fewer of {@code prototype}; empty entries are dropped. */
    public ItemCellContents shrunk(ItemStack prototype, int amount) {
        List<Entry> out = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            if (ItemStack.isSameItemSameComponents(entry.item(), prototype)) {
                int remaining = entry.count() - amount;
                if (remaining > 0) {
                    out.add(new Entry(entry.item(), remaining));
                }
            } else {
                out.add(entry);
            }
        }
        return new ItemCellContents(List.copyOf(out));
    }
}
