package za.co.neroland.nerologistics.network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * Tracks conduit {@link ConduitNetwork}s per dimension and per {@link NetworkMedium}, rebuilding
 * <em>incrementally</em> on place/break — never by scanning the world. Placing a conduit merges the
 * adjacent same-medium networks (absorbed into one); breaking one re-floods the survivors into
 * components. A per-network node cap ({@link NeroLogisticsConfig#maxNodesPerNetwork()}) makes
 * oversized builds refuse to connect (the conduit stays an isolated 1-node network) rather than
 * degrading server tick time. Server-side only.
 */
public final class NetworkManager {

    private static final Map<ResourceKey<Level>, Map<NetworkMedium, Map<BlockPos, ConduitNetwork>>> LEVELS =
            new HashMap<>();

    private NetworkManager() {
    }

    private static Map<BlockPos, ConduitNetwork> table(Level level, NetworkMedium medium) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> new EnumMap<>(NetworkMedium.class))
                .computeIfAbsent(medium, m -> new HashMap<>());
    }

    /** Register a newly placed/loaded conduit, merging adjacent same-medium networks. */
    public static void onPlaced(Level level, BlockPos pos, NetworkMedium medium) {
        if (level.isClientSide()) {
            return;
        }
        BlockPos key = pos.immutable();
        Map<BlockPos, ConduitNetwork> table = table(level, medium);
        if (table.containsKey(key)) {
            return; // already tracked (e.g. re-load of a still-mapped position)
        }
        Set<ConduitNetwork> adjacent = new LinkedHashSet<>();
        for (Direction dir : Direction.values()) {
            ConduitNetwork n = table.get(key.relative(dir));
            if (n != null) {
                adjacent.add(n);
            }
        }
        int cap = NeroLogisticsConfig.maxNodesPerNetwork();
        int combined = 1;
        for (ConduitNetwork n : adjacent) {
            combined += n.size();
        }
        if (adjacent.isEmpty() || combined > cap) {
            // Fresh isolated network: first node, or a merge that would exceed the cap (refuse to grow).
            ConduitNetwork solo = new ConduitNetwork(medium);
            solo.add(key);
            table.put(key, solo);
            return;
        }
        // Absorb every adjacent network plus the new node into one fresh network.
        ConduitNetwork merged = new ConduitNetwork(medium);
        for (ConduitNetwork n : adjacent) {
            for (BlockPos member : Set.copyOf(n.members())) {
                merged.add(member);
                table.put(member, merged);
            }
        }
        merged.add(key);
        table.put(key, merged);
    }

    /** Unregister a broken conduit and split its network into the surviving components. */
    public static void onRemoved(Level level, BlockPos pos, NetworkMedium medium) {
        if (level.isClientSide()) {
            return;
        }
        Map<BlockPos, ConduitNetwork> table = table(level, medium);
        ConduitNetwork net = table.remove(pos.immutable());
        if (net == null) {
            return;
        }
        net.remove(pos);
        Set<BlockPos> survivors = new HashSet<>(net.members());
        for (BlockPos p : survivors) {
            table.remove(p);
        }
        while (!survivors.isEmpty()) {
            BlockPos start = survivors.iterator().next();
            ConduitNetwork component = new ConduitNetwork(medium);
            Deque<BlockPos> stack = new ArrayDeque<>();
            stack.push(start);
            survivors.remove(start);
            while (!stack.isEmpty()) {
                BlockPos p = stack.pop();
                component.add(p);
                table.put(p, component);
                for (Direction dir : Direction.values()) {
                    BlockPos np = p.relative(dir);
                    if (survivors.remove(np)) {
                        stack.push(np);
                    }
                }
            }
        }
    }

    /** The network containing {@code pos}, or {@code null}. */
    public static ConduitNetwork networkAt(Level level, NetworkMedium medium, BlockPos pos) {
        return table(level, medium).get(pos);
    }

    /** Drop the cached endpoints of the network at {@code pos} (e.g. after a face-mode/neighbour change). */
    public static void invalidateAt(Level level, NetworkMedium medium, BlockPos pos) {
        ConduitNetwork net = table(level, medium).get(pos);
        if (net != null) {
            net.invalidate();
        }
    }

    /** Drive the network containing {@code pos} for this tick (idempotent per network per tick). */
    public static void tick(ServerLevel level, NetworkMedium medium, BlockPos pos) {
        ConduitNetwork net = table(level, medium).get(pos);
        if (net != null) {
            net.tick(level);
        }
    }
}
