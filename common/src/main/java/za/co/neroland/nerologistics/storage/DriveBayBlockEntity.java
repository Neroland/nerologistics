package za.co.neroland.nerologistics.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.menu.DriveBayMenu;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Drive Bay — six bays for {@link StorageCellItem storage cells}, joining the adjacent item/fluid
 * conduit networks as a digital {@link StorageNode}. Deliberately <b>not</b> a vanilla
 * {@code Container}: cells are digital media, so ducts, hoppers and the read-through index must
 * never vacuum the cell items themselves — the network sees only the cells' <em>contents</em>,
 * via the {@link StorageNode} contract that the {@link NetworkStorageIndex} discovers through the
 * endpoint cache. Passive (no ticker, no power); comparator output reads the average cell fill.
 *
 * <p>Insertion honours per-cell settings: partitioned cells that match the resource fill before
 * unpartitioned ones at equal priority, and higher priority always fills first. Every visible
 * mutation bumps {@link #storageVersion()} so index refreshes stay O(changed). Block-scoped state
 * only — no player data.</p>
 */
public class DriveBayBlockEntity extends BlockEntity implements StorageNode, MenuProvider {

    /** Fixed bay count (deliberately not configurable — the GUI and comparator scale assume it). */
    public static final int BAYS = 6;

    /** Cell bays. Menu edits route through {@link SimpleContainer#setChanged} → version bump. */
    private final SimpleContainer bays = new SimpleContainer(BAYS) {
        @Override
        public void setChanged() {
            super.setChanged();
            DriveBayBlockEntity.this.markStorageChanged();
        }
    };

    /** Monotonic content version (not persisted — the index only compares within a session). */
    private long version;

    public DriveBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRIVE_BAY.get(), pos, state);
    }

    public SimpleContainer bays() {
        return this.bays;
    }

    /** Bump the content version and persist — every visible mutation funnels through here. */
    public void markStorageChanged() {
        this.version++;
        setChanged();
    }

    /** Average fill fraction across installed cells (0–1), for the comparator. */
    public double fillFraction() {
        int cells = 0;
        double sum = 0;
        for (int i = 0; i < BAYS; i++) {
            ItemStack cell = this.bays.getItem(i);
            if (cell.getItem() instanceof StorageCellItem item) {
                cells++;
                long capacity = item.capacity();
                sum += capacity <= 0 ? 0 : (double) StorageCellItem.stored(cell) / capacity;
            }
        }
        return cells == 0 ? 0 : sum / cells;
    }

    // --- StorageNode ---------------------------------------------------------

    @Override
    public boolean storageNodeActive() {
        return NeroLogisticsConfig.enableStorageNetwork();
    }

    @Override
    public int storagePriority() {
        int best = Integer.MIN_VALUE;
        boolean any = false;
        for (int i = 0; i < BAYS; i++) {
            ItemStack cell = this.bays.getItem(i);
            if (cell.getItem() instanceof StorageCellItem) {
                best = Math.max(best, StorageCellItem.settings(cell).priority());
                any = true;
            }
        }
        return any ? best : 0;
    }

    @Override
    public long storageVersion() {
        return this.version;
    }

    @Override
    public long insertItem(ItemStack prototype, long amount, boolean simulate) {
        if (!storageNodeActive()) {
            return 0;
        }
        long moved = 0;
        for (ItemStack cell : cellsForInsert(StorageCellItem.Kind.ITEM, prototype, null)) {
            if (moved >= amount) {
                break;
            }
            moved += StorageCellItem.insertItem(cell, prototype, amount - moved, simulate);
        }
        if (moved > 0 && !simulate) {
            markStorageChanged();
        }
        return moved;
    }

    @Override
    public long extractItem(ItemStack prototype, long amount, boolean simulate) {
        if (!storageNodeActive()) {
            return 0;
        }
        long moved = 0;
        for (int i = 0; i < BAYS; i++) {
            if (moved >= amount) {
                break;
            }
            moved += StorageCellItem.extractItem(this.bays.getItem(i), prototype, amount - moved, simulate);
        }
        if (moved > 0 && !simulate) {
            markStorageChanged();
        }
        return moved;
    }

    @Override
    public void collectItems(BiConsumer<ItemStack, Long> out) {
        if (!storageNodeActive()) {
            return;
        }
        for (int i = 0; i < BAYS; i++) {
            for (ItemCellContents.Entry entry : StorageCellItem.itemContents(this.bays.getItem(i)).entries()) {
                out.accept(entry.item(), (long) entry.count());
            }
        }
    }

    @Override
    public long insertFluid(Fluid fluid, long amount, boolean simulate) {
        if (!storageNodeActive()) {
            return 0;
        }
        long moved = 0;
        for (ItemStack cell : cellsForInsert(StorageCellItem.Kind.FLUID, null, fluid)) {
            if (moved >= amount) {
                break;
            }
            moved += StorageCellItem.insertFluid(cell, fluid, amount - moved, simulate);
        }
        if (moved > 0 && !simulate) {
            markStorageChanged();
        }
        return moved;
    }

    @Override
    public long extractFluid(Fluid fluid, long amount, boolean simulate) {
        if (!storageNodeActive()) {
            return 0;
        }
        long moved = 0;
        for (int i = 0; i < BAYS; i++) {
            moved += StorageCellItem.extractFluid(this.bays.getItem(i), fluid, amount - moved, simulate);
            if (moved >= amount) {
                break;
            }
        }
        if (moved > 0 && !simulate) {
            markStorageChanged();
        }
        return moved;
    }

    @Override
    public void collectFluids(BiConsumer<Fluid, Long> out) {
        if (!storageNodeActive()) {
            return;
        }
        for (int i = 0; i < BAYS; i++) {
            for (FluidCellContents.Entry entry : StorageCellItem.fluidContents(this.bays.getItem(i)).entries()) {
                Fluid fluid = entry.fluid();
                if (entry.amount() > 0) {
                    out.accept(fluid, entry.amount());
                }
            }
        }
    }

    /**
     * The installed cells of {@code kind}, in insertion order: priority descending, and at equal
     * priority partitioned cells matching the resource before unpartitioned ones (partitioned
     * cells that do <em>not</em> match refuse in {@code StorageCellItem} anyway).
     */
    private List<ItemStack> cellsForInsert(StorageCellItem.Kind kind,
            @Nullable ItemStack prototype, @Nullable Fluid fluid) {
        List<ItemStack> cells = new ArrayList<>(BAYS);
        for (int i = 0; i < BAYS; i++) {
            ItemStack cell = this.bays.getItem(i);
            if (cell.getItem() instanceof StorageCellItem item && item.kind() == kind) {
                cells.add(cell);
            }
        }
        cells.sort(Comparator
                .comparingInt((ItemStack cell) -> StorageCellItem.settings(cell).priority()).reversed()
                .thenComparing(cell -> {
                    CellSettings settings = StorageCellItem.settings(cell);
                    boolean matches = settings.isPartitioned()
                            && (prototype != null ? settings.acceptsItem(prototype)
                                    : fluid != null && settings.acceptsFluid(fluid));
                    return matches ? 0 : 1;
                }));
        return cells;
    }

    // --- Menu ----------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.drive_bay");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new DriveBayMenu(id, playerInventory, this);
    }

    // --- Persistence ---------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < BAYS; i++) {
            output.store("Bay" + i, ItemStack.OPTIONAL_CODEC, this.bays.getItem(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < BAYS; i++) {
            this.bays.setItem(i, input.read("Bay" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        }
        this.version++;
    }
}
