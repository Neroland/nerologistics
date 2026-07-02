package za.co.neroland.nerologistics.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * Tracks cargo ports (per dimension + channel) and in-transit shipments. A shipment is held in
 * {@link ShipmentState} (durable {@code SavedData} on the overworld) for its transit time, so a
 * shipment in flight <b>survives a server restart</b> and resumes its timer; on arrival the
 * destination chunk is force-loaded <em>only momentarily</em> to deposit the payload (dropping any
 * overflow), then released — so two dimensions are never kept loaded for the transit duration.
 * Driven once per tick from the loader server-tick hooks.
 *
 * <p>The port registry stays in memory: ports re-register themselves as their block entities tick
 * after a world load, and deliveries resolve the destination container by position, not by registry.
 */
public final class ShipmentManager {

    private static final Map<ResourceKey<Level>, Map<Integer, List<BlockPos>>> PORTS = new HashMap<>();

    private ShipmentManager() {
    }

    private static List<BlockPos> ports(ResourceKey<Level> dim, int channel) {
        return PORTS.computeIfAbsent(dim, k -> new HashMap<>()).computeIfAbsent(channel, c -> new ArrayList<>());
    }

    public static void registerPort(Level level, BlockPos pos, int channel) {
        if (level.isClientSide()) {
            return;
        }
        List<BlockPos> list = ports(level.dimension(), channel);
        BlockPos key = pos.immutable();
        if (!list.contains(key)) {
            list.add(key);
        }
    }

    public static void unregisterPort(Level level, BlockPos pos, int channel) {
        if (level.isClientSide()) {
            return;
        }
        ports(level.dimension(), channel).remove(pos.immutable());
    }

    public static void rechannelPort(Level level, BlockPos pos, int oldChannel, int newChannel) {
        unregisterPort(level, pos, oldChannel);
        registerPort(level, pos, newChannel);
    }

    /** A destination port in {@code destDim} on {@code channel}, other than {@code exclude}, or null. */
    @Nullable
    public static BlockPos findPort(ResourceKey<Level> destDim, int channel, BlockPos exclude) {
        Map<Integer, List<BlockPos>> byChannel = PORTS.get(destDim);
        if (byChannel == null) {
            return null;
        }
        List<BlockPos> list = byChannel.get(channel);
        if (list == null) {
            return null;
        }
        for (BlockPos pos : list) {
            if (!pos.equals(exclude)) {
                return pos;
            }
        }
        return null;
    }

    /** Queue a shipment from {@code fromDim}/{@code fromPos}; it materialises after {@code transitTicks}. */
    public static void ship(MinecraftServer server, List<ItemStack> items, ResourceKey<Level> fromDim,
            BlockPos fromPos, ResourceKey<Level> destDim, BlockPos destPos, int transitTicks) {
        long now = server.overworld().getGameTime();
        ShipmentState.get(server).add(new CargoManifest(items, fromDim, fromPos.immutable(), destDim,
                destPos.immutable(), now, now + Math.max(1, transitTicks)));
    }

    /** Number of shipments currently in transit (for the dashboard). */
    public static int pendingCount(MinecraftServer server) {
        return ShipmentState.get(server).count();
    }

    /** Whether the in-transit queue is at its hard cap (ports must stop launching). */
    public static boolean atCapacity(MinecraftServer server) {
        return pendingCount(server) >= NeroLogisticsConfig.maxPendingShipments();
    }

    /** Deliver any shipments whose arrival tick has passed. Call once per server tick. */
    public static void tick(MinecraftServer server) {
        LogisticsMetrics.tick(server); // daily attribution retention prune
        ShipmentState state = ShipmentState.get(server);
        if (state.count() == 0) {
            return;
        }
        long now = server.overworld().getGameTime();
        for (CargoManifest manifest : state.drainDue(m -> now >= m.arrivalTick())) {
            deliver(server, manifest);
        }
    }

    private static void deliver(MinecraftServer server, CargoManifest manifest) {
        ServerLevel level = server.getLevel(manifest.destDim());
        if (level == null) {
            return; // destination dimension unavailable (v1: payload is dropped from tracking)
        }
        BlockPos pos = manifest.destPos();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        boolean forced = false;
        try {
            level.setChunkForced(cx, cz, true); // momentary — only for this delivery
            forced = true;
            Container dest = InventoryTransfer.containerAt(level, pos);
            for (ItemStack stack : manifest.items()) {
                if (stack.isEmpty()) {
                    continue;
                }
                int moved = dest != null ? InventoryTransfer.insert(dest, Direction.DOWN, stack, stack.getCount()) : 0;
                if (moved < stack.getCount()) {
                    ItemStack remainder = stack.copyWithCount(stack.getCount() - moved);
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, remainder);
                }
            }
            LogisticsMetrics.recordShipmentDelivered(level);
        } finally {
            if (forced) {
                level.setChunkForced(cx, cz, false);
            }
        }
    }
}
