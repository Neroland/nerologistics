package za.co.neroland.nerologistics.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.nerolandcore.progression.CoreGates;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerologistics.conduit.WirelessCargoTerminalBlockEntity;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * Groups wireless cargo terminals by dimension + channel, so terminals sharing a channel form a virtual
 * link with no physical conduit. Once per configurable interval (query-batching window) the registry
 * moves items from terminals whose buffer holds stock to in-range terminals whose buffer has space,
 * charging the sender's Core energy buffer per item. Gated behind {@code INDUSTRIAL_POWER}. Server-side
 * only; membership is cached and only touched on place/break.
 */
public final class WirelessRegistry {

    private static final Map<ResourceKey<Level>, Map<Integer, List<BlockPos>>> LEVELS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Integer, Long>> LAST_TICK = new HashMap<>();

    private WirelessRegistry() {
    }

    private static List<BlockPos> members(Level level, int channel) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> new HashMap<>())
                .computeIfAbsent(channel, c -> new ArrayList<>());
    }

    public static void onPlaced(Level level, BlockPos pos, int channel) {
        if (level.isClientSide()) {
            return;
        }
        List<BlockPos> list = members(level, channel);
        BlockPos key = pos.immutable();
        if (!list.contains(key)) {
            list.add(key);
        }
    }

    public static void onRemoved(Level level, BlockPos pos, int channel) {
        if (level.isClientSide()) {
            return;
        }
        members(level, channel).remove(pos.immutable());
    }

    /** Move a terminal between channels (re-keys its membership). */
    public static void rechannel(Level level, BlockPos pos, int oldChannel, int newChannel) {
        onRemoved(level, pos, oldChannel);
        onPlaced(level, pos, newChannel);
    }

    /** Drive one channel for this tick (idempotent per channel per interval). */
    public static void tick(ServerLevel level, int channel) {
        long now = level.getGameTime();
        Map<Integer, Long> last = LAST_TICK.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long previous = last.getOrDefault(channel, -1L);
        int interval = NeroLogisticsConfig.wirelessIntervalTicks();
        if (now == previous || now % interval != 0L) {
            return;
        }
        last.put(channel, now);

        MinecraftServer server = level.getServer();
        if (server != null && !ProgressionGates.isServerOpen(server, CoreGates.INDUSTRIAL_POWER)) {
            return;
        }
        List<BlockPos> list = members(level, channel);
        if (list.size() < 2) {
            return;
        }
        int range = NeroLogisticsConfig.wirelessRange();
        long rangeSq = (long) range * range;
        int costPerItem = NeroLogisticsConfig.wirelessEnergyPerItem();

        for (BlockPos srcPos : list) {
            WirelessCargoTerminalBlockEntity src = terminal(level, srcPos);
            if (src == null || src.isEmpty()) {
                continue;
            }
            for (int slot = 0; slot < src.getContainerSize(); slot++) {
                ItemStack stack = src.getItem(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                int affordable = costPerItem <= 0
                        ? stack.getCount()
                        : (int) Math.min(stack.getCount(), src.getEnergy().getAmount() / costPerItem);
                if (affordable <= 0) {
                    break; // no energy for this terminal
                }
                int movedTotal = 0;
                for (BlockPos dstPos : list) {
                    if (dstPos.equals(srcPos) || srcPos.distSqr(dstPos) > rangeSq) {
                        continue;
                    }
                    WirelessCargoTerminalBlockEntity dst = terminal(level, dstPos);
                    if (dst == null) {
                        continue;
                    }
                    int inserted = InventoryTransfer.insert(dst, Direction.UP, stack, affordable - movedTotal);
                    if (inserted > 0) {
                        movedTotal += inserted;
                        if (costPerItem > 0) {
                            src.energyBuffer().consume(inserted * costPerItem);
                        }
                    }
                    if (movedTotal >= affordable) {
                        break;
                    }
                }
                if (movedTotal > 0) {
                    stack.shrink(movedTotal);
                    src.setChanged();
                    LogisticsMetrics.recordItems(level, movedTotal);
                }
            }
        }
    }

    /** A snapshot of the terminal positions registered on {@code channel} in this dimension. */
    public static List<BlockPos> membersOf(Level level, int channel) {
        return new ArrayList<>(members(level, channel));
    }

    private static WirelessCargoTerminalBlockEntity terminal(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof WirelessCargoTerminalBlockEntity terminal ? terminal : null;
    }
}
