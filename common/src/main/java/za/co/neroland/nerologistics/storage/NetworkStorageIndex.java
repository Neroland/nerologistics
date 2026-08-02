package za.co.neroland.nerologistics.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.platform.FluidLookup;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.network.ConduitEndpoint;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * The AE2-like heart of the storage network: a lazily-built, aggressively-cached aggregation of
 * everything storable on one {@link ConduitNetwork} — (a) native {@link StorageNode drive bays},
 * (b) read-through of plain vanilla {@code Container}/{@code WorldlyContainer} endpoints
 * (warehouses, chests) on ITEM networks, and (c) Core {@link FluidLookup} endpoints on FLUID
 * networks. One index per network, obtained via {@link ConduitNetwork#storageIndex()}; the medium
 * of the owning network decides which half (items or fluids) is live.
 *
 * <p><b>Invariants (megabase-safe):</b></p>
 * <ul>
 *   <li><b>Never a per-tick full scan.</b> Nothing ticks the index; all work happens inside a
 *       query. Structure (which blocks participate) is rebuilt only when the network's endpoint
 *       cache was invalidated ({@link #invalidateStructure()} rides the existing
 *       {@code ConduitNetwork.invalidate()} path). Node contributions are re-collected only when
 *       a node's {@link StorageNode#storageVersion() version} moved — O(changed). Read-through
 *       containers are rescanned at most once per {@link NeroLogisticsConfig#storageIndexRefreshTicks()}
 *       (default 20t), and only when a query actually arrives — consumers gate all refresh work
 *       via {@link #openConsumer()}/{@link #closeConsumer()} (a terminal keeps one open while its
 *       GUI is up; one-shot callers may query without opening — each query pays for its own
 *       staleness check, never for background work).</li>
 *   <li><b>Hard caps.</b> At most {@link #MAX_READTHROUGH_SOURCES} external containers/tanks are
 *       indexed (excess endpoints are ignored, deterministically by endpoint order) and at most
 *       {@link #MAX_TRACKED_TYPES} distinct item/fluid types are aggregated (excess types stay
 *       stored but unlisted until space frees up). Endpoint count itself is already bounded by
 *       {@code maxNodesPerNetwork} × 6.</li>
 *   <li><b>Routing order.</b> Insert: highest-priority accepting cell first (drive bays sorted by
 *       {@link StorageNode#storagePriority()}, exact per-cell order inside the bay), then
 *       read-through containers. Extract: cells first, then containers. Item matching is exact
 *       (item + components) end to end.</li>
 *   <li><b>Master toggle.</b> With {@code enableStorageNetwork=false} the index is empty and all
 *       transfer calls return 0 (drive bays go inert on the same flag).</li>
 * </ul>
 *
 * <p>All state is network/block-scoped and server-side only — no player data (POPIA/GDPR).
 * Indexes die with their network object on merge/split and are lazily rebuilt.</p>
 */
public final class NetworkStorageIndex {

    /** Hard cap on distinct item/fluid types aggregated per network. */
    public static final int MAX_TRACKED_TYPES = 4096;

    /** Hard cap on read-through containers/tanks indexed per network. */
    public static final int MAX_READTHROUGH_SOURCES = 256;

    private final ConduitNetwork network;

    /** Set when the owning network's endpoint cache is invalidated; forces a structure rebuild. */
    private boolean structureDirty = true;

    /** Open consumer count (terminals etc.). Queries are allowed regardless; this gates nothing
     *  today beyond documentation, but lets later consumers (and tooling) reason about liveness. */
    private int consumers;

    private final List<NodeRef> nodes = new ArrayList<>();
    private final List<SourceRef> sources = new ArrayList<>();

    /** Aggregated totals; rebuilt from per-source contributions when any contribution changed. */
    private final Map<ItemKey, Long> itemTotals = new HashMap<>();
    private final Map<Fluid, Long> fluidTotals = new HashMap<>();
    private boolean totalsDirty = true;
    private long lastSourceScanTick = Long.MIN_VALUE;

    /** A native drive-bay node reference (resolved to a {@link StorageNode} each refresh). */
    private static final class NodeRef {
        final BlockPos pos;
        long lastVersion = Long.MIN_VALUE;
        final Map<ItemKey, Long> items = new HashMap<>();
        final Map<Fluid, Long> fluids = new HashMap<>();

        NodeRef(BlockPos pos) {
            this.pos = pos;
        }
    }

    /** A read-through external source: a vanilla container (ITEM) or Core fluid storage (FLUID). */
    private static final class SourceRef {
        final BlockPos pos;
        final Direction side;
        boolean dirty = true;
        final Map<ItemKey, Long> items = new HashMap<>();
        final Map<Fluid, Long> fluids = new HashMap<>();

        SourceRef(BlockPos pos, Direction side) {
            this.pos = pos;
            this.side = side;
        }
    }

    public NetworkStorageIndex(ConduitNetwork network) {
        this.network = network;
    }

    /** Drop the structural view; called from {@code ConduitNetwork.invalidate()}. */
    public void invalidateStructure() {
        this.structureDirty = true;
    }

    // --- Consumer gating -----------------------------------------------------

    /** A consumer (e.g. an open terminal) is now live; keep refreshes flowing while any is open. */
    public void openConsumer() {
        this.consumers++;
    }

    /** The matching close for {@link #openConsumer()} (clamped at zero). */
    public void closeConsumer() {
        this.consumers = Math.max(0, this.consumers - 1);
    }

    /** Number of currently open consumers (diagnostics / later QoS). */
    public int consumerCount() {
        return this.consumers;
    }

    // --- Snapshots -----------------------------------------------------------

    /**
     * Aggregated item contents (exact item+components key → total count). Unmodifiable view of
     * the live cache — valid until the next query or transfer; copy if retained. Empty unless the
     * owning network carries {@code ITEM}.
     */
    public Map<ItemKey, Long> itemSnapshot(Level level) {
        if (!NeroLogisticsConfig.enableStorageNetwork() || this.network.medium() != NetworkMedium.ITEM) {
            return Map.of();
        }
        refresh(level);
        return Collections.unmodifiableMap(this.itemTotals);
    }

    /**
     * Aggregated fluid contents (fluid → total mB). Unmodifiable view of the live cache — valid
     * until the next query or transfer; copy if retained. Empty unless the owning network carries
     * {@code FLUID}.
     */
    public Map<Fluid, Long> fluidSnapshot(Level level) {
        if (!NeroLogisticsConfig.enableStorageNetwork() || this.network.medium() != NetworkMedium.FLUID) {
            return Map.of();
        }
        refresh(level);
        return Collections.unmodifiableMap(this.fluidTotals);
    }

    // --- Transfer ------------------------------------------------------------

    /**
     * Insert up to {@code amount} of {@code prototype} into network storage: highest-priority
     * accepting drive bay first, then read-through containers. @return items accepted.
     */
    public long insertItem(Level level, ItemStack prototype, long amount, boolean simulate) {
        if (!active(NetworkMedium.ITEM) || prototype.isEmpty() || amount <= 0) {
            return 0;
        }
        rebuildStructureIfNeeded(level);
        long moved = 0;
        for (NodeRef ref : nodesByPriority(level)) {
            if (moved >= amount) {
                break;
            }
            StorageNode node = nodeAt(level, ref.pos);
            if (node != null) {
                moved += node.insertItem(prototype, amount - moved, simulate);
            }
        }
        for (SourceRef ref : this.sources) {
            if (moved >= amount) {
                break;
            }
            Container container = InventoryTransfer.containerAt(level, ref.pos);
            if (container == null) {
                continue;
            }
            int want = (int) Math.min(amount - moved, Integer.MAX_VALUE);
            int done = simulate
                    ? InventoryTransfer.simulateInsert(container, ref.side, prototype, want)
                    : InventoryTransfer.insert(container, ref.side, prototype, want);
            if (done > 0 && !simulate) {
                ref.dirty = true;
                this.totalsDirty = true;
            }
            moved += done;
        }
        return moved;
    }

    /**
     * Extract up to {@code amount} of {@code prototype} (exact item+components match) from network
     * storage: drive bays first, then read-through containers. @return items taken.
     */
    public long extractItem(Level level, ItemStack prototype, long amount, boolean simulate) {
        if (!active(NetworkMedium.ITEM) || prototype.isEmpty() || amount <= 0) {
            return 0;
        }
        rebuildStructureIfNeeded(level);
        long moved = 0;
        for (NodeRef ref : this.nodes) {
            if (moved >= amount) {
                break;
            }
            StorageNode node = nodeAt(level, ref.pos);
            if (node != null) {
                moved += node.extractItem(prototype, amount - moved, simulate);
            }
        }
        for (SourceRef ref : this.sources) {
            if (moved >= amount) {
                break;
            }
            Container container = InventoryTransfer.containerAt(level, ref.pos);
            if (container == null) {
                continue;
            }
            int want = (int) Math.min(amount - moved, Integer.MAX_VALUE);
            int done = simulate
                    ? Math.min(want, InventoryTransfer.countExact(container, ref.side, prototype))
                    : InventoryTransfer.extractExact(container, ref.side, prototype, want);
            if (done > 0 && !simulate) {
                ref.dirty = true;
                this.totalsDirty = true;
            }
            moved += done;
        }
        return moved;
    }

    /** Fluid twin of {@link #insertItem}: drive-bay fluid cells first, then Core fluid endpoints. */
    public long insertFluid(Level level, Fluid fluid, long amount, boolean simulate) {
        if (!active(NetworkMedium.FLUID) || fluid == Fluids.EMPTY || amount <= 0) {
            return 0;
        }
        rebuildStructureIfNeeded(level);
        long moved = 0;
        for (NodeRef ref : nodesByPriority(level)) {
            if (moved >= amount) {
                break;
            }
            StorageNode node = nodeAt(level, ref.pos);
            if (node != null) {
                moved += node.insertFluid(fluid, amount - moved, simulate);
            }
        }
        for (SourceRef ref : this.sources) {
            if (moved >= amount) {
                break;
            }
            NeroFluidStorage storage = FluidLookup.INSTANCE.find(level, ref.pos, ref.side);
            if (storage == null) {
                continue;
            }
            long done = storage.fill(fluid, amount - moved, simulate);
            if (done > 0 && !simulate) {
                ref.dirty = true;
                this.totalsDirty = true;
            }
            moved += done;
        }
        return moved;
    }

    /** Fluid twin of {@link #extractItem}: drive-bay fluid cells first, then Core fluid endpoints. */
    public long extractFluid(Level level, Fluid fluid, long amount, boolean simulate) {
        if (!active(NetworkMedium.FLUID) || fluid == Fluids.EMPTY || amount <= 0) {
            return 0;
        }
        rebuildStructureIfNeeded(level);
        long moved = 0;
        for (NodeRef ref : this.nodes) {
            if (moved >= amount) {
                break;
            }
            StorageNode node = nodeAt(level, ref.pos);
            if (node != null) {
                moved += node.extractFluid(fluid, amount - moved, simulate);
            }
        }
        for (SourceRef ref : this.sources) {
            if (moved >= amount) {
                break;
            }
            NeroFluidStorage storage = FluidLookup.INSTANCE.find(level, ref.pos, ref.side);
            if (storage == null || storage.getFluid() != fluid) {
                continue;
            }
            long done = storage.drain(amount - moved, simulate);
            if (done > 0 && !simulate) {
                ref.dirty = true;
                this.totalsDirty = true;
            }
            moved += done;
        }
        return moved;
    }

    // --- Internals -----------------------------------------------------------

    private boolean active(NetworkMedium medium) {
        return NeroLogisticsConfig.enableStorageNetwork() && this.network.medium() == medium;
    }

    /** Rebuild the participant lists from the network's cached endpoints (dedup by neighbour). */
    private void rebuildStructureIfNeeded(Level level) {
        // endpoints(level) lazily recomputes after ConduitNetwork.invalidate(), which also set our
        // structureDirty — so a fresh endpoint list and a fresh participant list stay in lockstep.
        List<ConduitEndpoint> endpoints = this.network.endpoints(level);
        if (!this.structureDirty) {
            return;
        }
        this.structureDirty = false;
        this.nodes.clear();
        this.sources.clear();
        this.totalsDirty = true;
        this.lastSourceScanTick = Long.MIN_VALUE;
        Set<BlockPos> seen = new HashSet<>();
        for (ConduitEndpoint ep : endpoints) {
            BlockPos neighbor = ep.neighborPos();
            if (!seen.add(neighbor)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof StorageNode) {
                this.nodes.add(new NodeRef(neighbor.immutable()));
            } else if (this.sources.size() < MAX_READTHROUGH_SOURCES) {
                if (this.network.medium() == NetworkMedium.ITEM && be instanceof Container) {
                    this.sources.add(new SourceRef(neighbor.immutable(), ep.neighborSide()));
                } else if (this.network.medium() == NetworkMedium.FLUID
                        && FluidLookup.INSTANCE.find(level, neighbor, ep.neighborSide()) != null) {
                    this.sources.add(new SourceRef(neighbor.immutable(), ep.neighborSide()));
                }
            }
        }
    }

    /** Bring contributions and totals up to date; the only place scans happen. */
    private void refresh(Level level) {
        rebuildStructureIfNeeded(level);
        // O(changed): only nodes whose version moved are re-collected.
        for (NodeRef ref : this.nodes) {
            StorageNode node = nodeAt(level, ref.pos);
            long version = node == null ? Long.MIN_VALUE + 1 : node.storageVersion();
            if (version == ref.lastVersion) {
                continue;
            }
            ref.lastVersion = version;
            ref.items.clear();
            ref.fluids.clear();
            if (node != null) {
                if (this.network.medium() == NetworkMedium.ITEM) {
                    node.collectItems((proto, count) -> ref.items.merge(ItemKey.of(proto), count, Long::sum));
                } else {
                    node.collectFluids((fluid, amount) -> ref.fluids.merge(fluid, amount, Long::sum));
                }
            }
            this.totalsDirty = true;
        }
        // Read-through sources: full rescan at most once per cooldown; dirty ones always rescan.
        long now = level.getGameTime();
        boolean cooldownElapsed = now - this.lastSourceScanTick >= NeroLogisticsConfig.storageIndexRefreshTicks();
        for (SourceRef ref : this.sources) {
            if (!ref.dirty && !cooldownElapsed) {
                continue;
            }
            ref.dirty = false;
            ref.items.clear();
            ref.fluids.clear();
            if (this.network.medium() == NetworkMedium.ITEM) {
                Container container = InventoryTransfer.containerAt(level, ref.pos);
                if (container != null) {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        ItemStack stack = container.getItem(slot);
                        if (!stack.isEmpty()) {
                            ref.items.merge(ItemKey.of(stack), (long) stack.getCount(), Long::sum);
                        }
                    }
                }
            } else {
                NeroFluidStorage storage = FluidLookup.INSTANCE.find(level, ref.pos, ref.side);
                if (storage != null && storage.getFluid() != Fluids.EMPTY && storage.getAmount() > 0) {
                    ref.fluids.merge(storage.getFluid(), storage.getAmount(), Long::sum);
                }
            }
            this.totalsDirty = true;
        }
        if (cooldownElapsed) {
            this.lastSourceScanTick = now;
        }
        if (this.totalsDirty) {
            this.totalsDirty = false;
            this.itemTotals.clear();
            this.fluidTotals.clear();
            for (NodeRef ref : this.nodes) {
                mergeCapped(ref.items, ref.fluids);
            }
            for (SourceRef ref : this.sources) {
                mergeCapped(ref.items, ref.fluids);
            }
        }
    }

    /** Merge a contribution into the totals, respecting the distinct-type hard cap. */
    private void mergeCapped(Map<ItemKey, Long> items, Map<Fluid, Long> fluids) {
        for (Map.Entry<ItemKey, Long> entry : items.entrySet()) {
            if (this.itemTotals.size() >= MAX_TRACKED_TYPES && !this.itemTotals.containsKey(entry.getKey())) {
                continue;
            }
            this.itemTotals.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        for (Map.Entry<Fluid, Long> entry : fluids.entrySet()) {
            if (this.fluidTotals.size() >= MAX_TRACKED_TYPES && !this.fluidTotals.containsKey(entry.getKey())) {
                continue;
            }
            this.fluidTotals.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    /** Drive-bay refs sorted by live priority (descending) for insertion routing. */
    private List<NodeRef> nodesByPriority(Level level) {
        if (this.nodes.size() <= 1) {
            return this.nodes;
        }
        List<NodeRef> sorted = new ArrayList<>(this.nodes);
        sorted.sort((a, b) -> Integer.compare(priorityAt(level, b.pos), priorityAt(level, a.pos)));
        return sorted;
    }

    private static int priorityAt(Level level, BlockPos pos) {
        StorageNode node = nodeAt(level, pos);
        return node == null ? Integer.MIN_VALUE : node.storagePriority();
    }

    private static StorageNode nodeAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof StorageNode node && node.storageNodeActive() ? node : null;
    }
}
