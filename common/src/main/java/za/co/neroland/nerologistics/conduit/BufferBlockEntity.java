package za.co.neroland.nerologistics.conduit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
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

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.menu.BufferMenu;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * Buffer block — holds a set amount of items on the network, in one of two per-buffer modes:
 *
 * <ul>
 *   <li><b>Keep-stocked leveling</b>: maintains a target item at a target quantity. The target (item +
 *       desired count) is set in the GUI's ghost slot — the stack's <em>count</em> is the level to hold.
 *       Each interval it pulls top-ups from the inventories on the attached item network when short, and
 *       releases the overflow back to the network when over.</li>
 *   <li><b>Passive cache</b>: simply holds whatever is in it as a throughput/overflow reservoir; ducts
 *       move items in and out, the buffer takes no action.</li>
 * </ul>
 *
 * <p>Keep-stocked integrates with native storage and auto-crafting implicitly: pulling its target down
 * draws on network stock, which an {@link AutoCrafterBlockEntity} can be set to replenish. Passive by
 * default; sneak-click the block to toggle mode. No power required.
 */
public class BufferBlockEntity extends AbstractTerminalBlockEntity implements MenuProvider {

    public enum Mode {
        PASSIVE,
        KEEP_STOCKED
    }

    public static final int BUFFER_SIZE = 9;

    /** Ghost target: slot 0 holds {item = what to keep, count = how many to keep}. */
    private final SimpleContainer target = new SimpleContainer(1);
    private Mode mode = Mode.PASSIVE;

    public BufferBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUFFER.get(), pos, state, BUFFER_SIZE, 0, 0);
    }

    public SimpleContainer target() {
        return this.target;
    }

    public Mode mode() {
        return this.mode;
    }

    /** Cycle PASSIVE ↔ KEEP_STOCKED; returns the new mode. */
    public Mode cycleMode() {
        this.mode = this.mode == Mode.PASSIVE ? Mode.KEEP_STOCKED : Mode.PASSIVE;
        setChanged();
        return this.mode;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.buffer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new BufferMenu(id, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Mode", this.mode.ordinal());
        output.store("Target", ItemStack.OPTIONAL_CODEC, this.target.getItem(0));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int ordinal = input.getIntOr("Mode", Mode.PASSIVE.ordinal());
        Mode[] modes = Mode.values();
        this.mode = ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : Mode.PASSIVE;
        this.target.setItem(0, input.read("Target", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BufferBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }
        if (!NeroLogisticsConfig.enableBuffers() || be.mode != Mode.KEEP_STOCKED) {
            return;
        }
        if (level.getGameTime() % NeroLogisticsConfig.bufferIntervalTicks() != 0L) {
            return;
        }
        be.level(level, pos);
    }

    /** Keep the buffer at its target level: top up when short, release the overflow when over. */
    private void level(Level level, BlockPos pos) {
        ItemStack want = this.target.getItem(0);
        if (want.isEmpty()) {
            return;
        }
        int desired = want.getCount();
        int current = countInBuffer(want);
        if (current == desired) {
            return;
        }
        ConduitNetwork network = adjacentItemNetwork(level, pos);
        if (network == null) {
            return;
        }
        List<Ext> externals = externals(level, network);
        if (externals.isEmpty()) {
            return;
        }
        if (current < desired) {
            pullUp(externals, want, Math.min(desired - current, roomFor(want)));
        } else {
            releaseDown(externals, want, current - desired);
        }
    }

    private void pullUp(List<Ext> externals, ItemStack want, int amount) {
        int need = amount;
        for (Ext ext : externals) {
            if (need <= 0) {
                break;
            }
            int got = InventoryTransfer.extract(ext.container, ext.side, want, need);
            if (got > 0) {
                int inserted = InventoryTransfer.insert(this, Direction.UP, want, got);
                if (inserted < got) {
                    // Couldn't fit it all (shouldn't happen — bounded by room); return the remainder.
                    ext.container.setChanged();
                }
                need -= inserted;
            }
        }
        setChanged();
    }

    private void releaseDown(List<Ext> externals, ItemStack want, int excess) {
        int remaining = excess;
        for (Ext ext : externals) {
            if (remaining <= 0) {
                break;
            }
            int accepted = InventoryTransfer.insert(ext.container, ext.side, want, remaining);
            if (accepted > 0) {
                InventoryTransfer.extract(this, Direction.UP, want, accepted);
                remaining -= accepted;
            }
        }
        setChanged();
    }

    private int countInBuffer(ItemStack match) {
        int total = 0;
        for (int i = 0; i < this.buffer.getContainerSize(); i++) {
            ItemStack slot = this.buffer.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItem(slot, match)) {
                total += slot.getCount();
            }
        }
        return total;
    }

    private int roomFor(ItemStack match) {
        int room = 0;
        int max = Math.min(match.getMaxStackSize(), this.buffer.getMaxStackSize());
        for (int i = 0; i < this.buffer.getContainerSize(); i++) {
            ItemStack slot = this.buffer.getItem(i);
            if (slot.isEmpty()) {
                room += max;
            } else if (ItemStack.isSameItemSameComponents(slot, match)) {
                room += Math.max(0, max - slot.getCount());
            }
        }
        return room;
    }

    private List<Ext> externals(Level level, ConduitNetwork network) {
        Map<BlockPos, Ext> map = new HashMap<>();
        for (BlockPos member : network.members()) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = member.relative(dir);
                if (network.contains(neighbor) || map.containsKey(neighbor)) {
                    continue;
                }
                Container container = InventoryTransfer.containerAt(level, neighbor);
                if (container == null || container == this) {
                    continue;
                }
                map.put(neighbor.immutable(), new Ext(container, dir.getOpposite()));
            }
        }
        return new ArrayList<>(map.values());
    }

    @Nullable
    private ConduitNetwork adjacentItemNetwork(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof AbstractConduitBlockEntity conduit && conduit.media().contains(NetworkMedium.ITEM)) {
                ConduitNetwork net = NetworkManager.networkAt(level, NetworkMedium.ITEM, neighbor);
                if (net != null) {
                    return net;
                }
            }
        }
        return null;
    }

    /** An external inventory on the network and the side to access it from. */
    private record Ext(Container container, Direction side) {
    }
}
