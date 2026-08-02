package za.co.neroland.nerologistics.storage;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.menu.CellConfigMenu;
import za.co.neroland.nerologistics.menu.MenuOpener;
import za.co.neroland.nerologistics.registry.ModDataComponents;

/**
 * A digital <b>storage cell</b> — the portable heart of the storage network. Contents live on the
 * cell stack itself (data components), so a cell moves between {@code Drive Bay}s with its items or
 * fluids intact. Four item tiers (1k / 8k / 64k / 512k total items) and four fluid tiers (16 / 128 /
 * 1024 / 8192 buckets); capacity is a pure count — no byte/type math, unlimited distinct types
 * within the total (this is deliberately <i>not</i> AE2's cell model).
 *
 * <p><b>Sneak-use</b> (in air) opens the cell configuration menu: a 9-slot partition (empty =
 * accept anything; fluid cells are partitioned with bucket ghosts) and a signed priority (higher
 * fills first). Cells never stack, and a drive bay is not a vanilla container, so the item network
 * cannot vacuum a cell out of its bay. All state is on the stack — no player data.
 */
public class StorageCellItem extends Item {

    /** What the cell stores. */
    public enum Kind {
        ITEM,
        FLUID
    }

    private final Kind kind;
    /** Tier index 0–3 (1k/8k/64k/512k items, or 16/128/1024/8192 buckets). */
    private final int tier;

    public StorageCellItem(Properties properties, Kind kind, int tier) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }

    public Kind kind() {
        return this.kind;
    }

    public int tier() {
        return this.tier;
    }

    /** Total capacity: items for item cells, mB for fluid cells (config-driven per tier). */
    public long capacity() {
        return this.kind == Kind.ITEM
                ? NeroLogisticsConfig.itemCellCapacity(this.tier)
                : NeroLogisticsConfig.fluidCellCapacityBuckets(this.tier) * 1000L;
    }

    // --- Component accessors -------------------------------------------------

    /** The settings (partition + priority) on {@code cell}, or defaults. */
    public static CellSettings settings(ItemStack cell) {
        return cell.getOrDefault(ModDataComponents.CELL_SETTINGS.get(), CellSettings.DEFAULT);
    }

    /** Store {@code settings} on {@code cell} (removes the component when default-equivalent). */
    public static void storeSettings(ItemStack cell, CellSettings settings) {
        if (!settings.isPartitioned() && settings.priority() == 0) {
            cell.remove(ModDataComponents.CELL_SETTINGS.get());
        } else {
            cell.set(ModDataComponents.CELL_SETTINGS.get(), settings);
        }
    }

    /** The stored item contents of {@code cell} (empty for fluid cells / non-cells). */
    public static ItemCellContents itemContents(ItemStack cell) {
        return cell.getOrDefault(ModDataComponents.ITEM_CELL_CONTENTS.get(), ItemCellContents.EMPTY);
    }

    /** The stored fluid contents of {@code cell} (empty for item cells / non-cells). */
    public static FluidCellContents fluidContents(ItemStack cell) {
        return cell.getOrDefault(ModDataComponents.FLUID_CELL_CONTENTS.get(), FluidCellContents.EMPTY);
    }

    // --- Cell transfer ops (drive bay + index call these) --------------------

    /**
     * Insert up to {@code amount} of {@code prototype} into item cell {@code cell}, honouring its
     * partition and remaining capacity. @return items accepted (0 for non-item-cells).
     */
    public static long insertItem(ItemStack cell, ItemStack prototype, long amount, boolean simulate) {
        if (amount <= 0 || prototype.isEmpty()
                || !(cell.getItem() instanceof StorageCellItem item) || item.kind != Kind.ITEM) {
            return 0;
        }
        if (!settings(cell).acceptsItem(prototype)) {
            return 0;
        }
        ItemCellContents contents = itemContents(cell);
        long room = item.capacity() - contents.total();
        long accepted = Math.min(amount, Math.min(room, Integer.MAX_VALUE));
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            cell.set(ModDataComponents.ITEM_CELL_CONTENTS.get(), contents.grown(prototype, (int) accepted));
        }
        return accepted;
    }

    /** Extract up to {@code amount} of {@code prototype} from item cell {@code cell}. @return items taken. */
    public static long extractItem(ItemStack cell, ItemStack prototype, long amount, boolean simulate) {
        if (amount <= 0 || prototype.isEmpty()
                || !(cell.getItem() instanceof StorageCellItem item) || item.kind != Kind.ITEM) {
            return 0;
        }
        ItemCellContents contents = itemContents(cell);
        long taken = Math.min(amount, contents.countOf(prototype));
        if (taken <= 0) {
            return 0;
        }
        if (!simulate) {
            ItemCellContents shrunk = contents.shrunk(prototype, (int) Math.min(taken, Integer.MAX_VALUE));
            if (shrunk.entries().isEmpty()) {
                cell.remove(ModDataComponents.ITEM_CELL_CONTENTS.get());
            } else {
                cell.set(ModDataComponents.ITEM_CELL_CONTENTS.get(), shrunk);
            }
        }
        return taken;
    }

    /**
     * Fill up to {@code amount} mB of {@code fluid} into fluid cell {@code cell}, honouring its
     * partition and remaining capacity. @return mB accepted (0 for non-fluid-cells).
     */
    public static long insertFluid(ItemStack cell, Fluid fluid, long amount, boolean simulate) {
        if (amount <= 0 || fluid == Fluids.EMPTY
                || !(cell.getItem() instanceof StorageCellItem item) || item.kind != Kind.FLUID) {
            return 0;
        }
        if (!settings(cell).acceptsFluid(fluid)) {
            return 0;
        }
        FluidCellContents contents = fluidContents(cell);
        long accepted = Math.min(amount, item.capacity() - contents.total());
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            cell.set(ModDataComponents.FLUID_CELL_CONTENTS.get(), contents.grown(fluid, accepted));
        }
        return accepted;
    }

    /** Drain up to {@code amount} mB of {@code fluid} from fluid cell {@code cell}. @return mB taken. */
    public static long extractFluid(ItemStack cell, Fluid fluid, long amount, boolean simulate) {
        if (amount <= 0 || fluid == Fluids.EMPTY
                || !(cell.getItem() instanceof StorageCellItem item) || item.kind != Kind.FLUID) {
            return 0;
        }
        FluidCellContents contents = fluidContents(cell);
        long taken = Math.min(amount, contents.amountOf(fluid));
        if (taken <= 0) {
            return 0;
        }
        if (!simulate) {
            FluidCellContents shrunk = contents.shrunk(fluid, taken);
            if (shrunk.entries().isEmpty()) {
                cell.remove(ModDataComponents.FLUID_CELL_CONTENTS.get());
            } else {
                cell.set(ModDataComponents.FLUID_CELL_CONTENTS.get(), shrunk);
            }
        }
        return taken;
    }

    /** Stored total (items or mB) of {@code cell} — for tooltips and the drive bay comparator. */
    public static long stored(ItemStack cell) {
        if (!(cell.getItem() instanceof StorageCellItem item)) {
            return 0;
        }
        return item.kind == Kind.ITEM ? itemContents(cell).total() : fluidContents(cell).total();
    }

    // --- Player interaction ---------------------------------------------------

    /** Sneak-use in air: open the partition/priority configuration menu for the held cell. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MenuOpener.open(serverPlayer, new SimpleMenuProvider(
                    (id, inventory, p) -> new CellConfigMenu(id, inventory, hand),
                    Component.translatable("gui.nerologistics.cell_config")));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        CellSettings settings = settings(stack);
        if (this.kind == Kind.ITEM) {
            ItemCellContents contents = itemContents(stack);
            tooltip.accept(Component.translatable("item.nerologistics.cell.fill_items",
                    contents.total(), capacity(), contents.typeCount()).withStyle(ChatFormatting.GRAY));
        } else {
            FluidCellContents contents = fluidContents(stack);
            tooltip.accept(Component.translatable("item.nerologistics.cell.fill_fluids",
                    contents.total(), capacity(), contents.typeCount()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("item.nerologistics.cell.priority", settings.priority())
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(settings.isPartitioned()
                ? Component.translatable("item.nerologistics.cell.partitioned", settings.partitionCount())
                        .withStyle(ChatFormatting.GOLD)
                : Component.translatable("item.nerologistics.cell.unpartitioned")
                        .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.nerologistics.cell.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
