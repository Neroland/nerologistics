package za.co.neroland.nerologistics.storage;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The stored inventory of a <b>fluid</b> storage cell, kept in the
 * {@code nerologistics:fluid_cell_contents} data component so a cell is fully portable. Entries
 * are keyed by fluid id (fluids carry no data components); amounts are millibuckets. Capacity is a
 * pure total mB count — unlimited distinct fluids within it. Immutable — every mutation returns a
 * new value, as data components require.
 */
public record FluidCellContents(List<Entry> entries) {

    /** One stored line: a fluid id (string form, resolved lazily) and the stored mB. */
    public record Entry(String fluidId, long amount) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("fluid").forGetter(Entry::fluidId),
                Codec.LONG.fieldOf("amount").forGetter(Entry::amount)
        ).apply(inst, Entry::new));

        /** The stored fluid, or {@code Fluids.EMPTY} if the id no longer resolves (mod removed). */
        public Fluid fluid() {
            return BuiltInRegistries.FLUID.getValue(Identifier.parse(this.fluidId));
        }
    }

    /** Empty cell (component absent ≡ this). */
    public static final FluidCellContents EMPTY = new FluidCellContents(List.of());

    public static final Codec<FluidCellContents> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(FluidCellContents::entries)
    ).apply(inst, FluidCellContents::new));

    private static String idOf(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }

    /** Total mB stored across all entries. */
    public long total() {
        long total = 0;
        for (Entry entry : this.entries) {
            total += entry.amount();
        }
        return total;
    }

    /** Number of distinct fluids stored. */
    public int typeCount() {
        return this.entries.size();
    }

    /** Stored mB of {@code fluid}. */
    public long amountOf(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return 0;
        }
        String id = idOf(fluid);
        for (Entry entry : this.entries) {
            if (entry.fluidId().equals(id)) {
                return entry.amount();
            }
        }
        return 0;
    }

    /** A copy with {@code amount} more mB of {@code fluid} (caller enforces capacity). */
    public FluidCellContents grown(Fluid fluid, long amount) {
        String id = idOf(fluid);
        List<Entry> out = new ArrayList<>(this.entries.size() + 1);
        boolean merged = false;
        for (Entry entry : this.entries) {
            if (!merged && entry.fluidId().equals(id)) {
                out.add(new Entry(id, entry.amount() + amount));
                merged = true;
            } else {
                out.add(entry);
            }
        }
        if (!merged) {
            out.add(new Entry(id, amount));
        }
        return new FluidCellContents(List.copyOf(out));
    }

    /** A copy with up to {@code amount} fewer mB of {@code fluid}; empty entries are dropped. */
    public FluidCellContents shrunk(Fluid fluid, long amount) {
        String id = idOf(fluid);
        List<Entry> out = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            if (entry.fluidId().equals(id)) {
                long remaining = entry.amount() - amount;
                if (remaining > 0) {
                    out.add(new Entry(id, remaining));
                }
            } else {
                out.add(entry);
            }
        }
        return new FluidCellContents(List.copyOf(out));
    }
}
