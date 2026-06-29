package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.progression.CoreGates;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.filter.ItemFilter;
import za.co.neroland.nerologistics.menu.StorageRequestMenu;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * Storage request terminal: a GUI block that aggregates the item network it is attached to. Every
 * configurable interval it restocks its output buffer with items pulled from the inventories on the
 * adjacent item duct's network, matching a request {@link ItemFilter} (default: everything). Players
 * take items from the buffer through the menu. Native aggregation only — when AE2 is present its
 * interface is just another {@link Container} on the network, so it is read through the same path with
 * no hard AE2 dependency.
 */
public class StorageRequestTerminalBlockEntity extends AbstractTerminalBlockEntity implements MenuProvider {

    public static final int BUFFER_SIZE = 9;
    public static final int ENERGY_CAPACITY = 50_000;
    public static final int ENERGY_MAX_IO = 1_000;

    private final ItemFilter requestFilter = new ItemFilter();

    public StorageRequestTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_REQUEST_TERMINAL.get(), pos, state, BUFFER_SIZE, ENERGY_CAPACITY, ENERGY_MAX_IO);
        // Default to a blacklist with no rules => request everything until the player narrows it.
        this.requestFilter.setWhitelist(false);
    }

    public ItemFilter requestFilter() {
        return this.requestFilter;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.storage_request_terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new StorageRequestMenu(id, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.requestFilter.save(output, "req_");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.requestFilter.load(input, "req_");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            StorageRequestTerminalBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }
        if (level.getServer() != null
                && !ProgressionGates.isServerOpen(level.getServer(), CoreGates.INDUSTRIAL_POWER)) {
            return;
        }
        if (level.getGameTime() % NeroLogisticsConfig.wirelessIntervalTicks() != 0L) {
            return;
        }
        be.restock(level, pos);
    }

    /** Pull filter-matching items from the attached item network's inventories into the buffer. */
    private void restock(Level level, BlockPos pos) {
        ConduitNetwork network = adjacentItemNetwork(level, pos);
        if (network == null) {
            return;
        }
        int budget = NeroLogisticsConfig.itemTransferPerTick();
        for (BlockPos member : network.members()) {
            if (budget <= 0) {
                break;
            }
            for (Direction dir : Direction.values()) {
                if (budget <= 0) {
                    break;
                }
                BlockPos neighbor = member.relative(dir);
                if (network.contains(neighbor)) {
                    continue;
                }
                Container source = InventoryTransfer.containerAt(level, neighbor);
                if (source == null || source == this) {
                    continue;
                }
                Direction side = dir.getOpposite();
                int slot = -1;
                while (budget > 0 && (slot = InventoryTransfer.firstExtractableSlot(source, side, slot)) >= 0) {
                    ItemStack stack = source.getItem(slot);
                    if (stack.isEmpty() || !this.requestFilter.test(stack)) {
                        continue;
                    }
                    int moved = InventoryTransfer.insert(this, Direction.UP, stack, Math.min(stack.getCount(), budget));
                    if (moved > 0) {
                        stack.shrink(moved);
                        source.setChanged();
                        budget -= moved;
                    }
                }
            }
        }
    }

    @Nullable
    private ConduitNetwork adjacentItemNetwork(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof ItemDuctBlockEntity) {
                ConduitNetwork net = NetworkManager.networkAt(level, NetworkMedium.ITEM, neighbor);
                if (net != null) {
                    return net;
                }
            }
        }
        return null;
    }
}
