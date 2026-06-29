package za.co.neroland.nerologistics.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.platform.EnergyLookup;
import za.co.neroland.nerolandcore.platform.FluidLookup;
import za.co.neroland.nerolandcore.sideconfig.SideMode;

import za.co.neroland.nerologistics.conduit.AbstractConduitBlockEntity;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * One connected component of same-medium conduits in a single dimension. Holds its member
 * positions and a lazily-cached endpoint list (faces touching external inventories). Transport
 * runs at most once per game tick, round-based, bounded by a per-tick throughput budget. Conduits
 * buffer nothing — a tick moves resources straight from source endpoints to sink endpoints.
 */
public final class ConduitNetwork {

    private final NetworkMedium medium;
    private final Set<BlockPos> members = new HashSet<>();
    private List<ConduitEndpoint> endpoints;
    private long lastTickGame = -1L;

    public ConduitNetwork(NetworkMedium medium) {
        this.medium = medium;
    }

    public NetworkMedium medium() {
        return this.medium;
    }

    public Set<BlockPos> members() {
        return this.members;
    }

    public int size() {
        return this.members.size();
    }

    public boolean contains(BlockPos pos) {
        return this.members.contains(pos);
    }

    public void add(BlockPos pos) {
        this.members.add(pos.immutable());
        invalidate();
    }

    public void remove(BlockPos pos) {
        this.members.remove(pos);
        invalidate();
    }

    /** Drop the cached endpoint list; recomputed on next access. */
    public void invalidate() {
        this.endpoints = null;
    }

    /** Number of external interaction points (faces touching non-conduit blocks). For the dashboard. */
    public int endpointCount(Level level) {
        return endpoints(level).size();
    }

    private List<ConduitEndpoint> endpoints(Level level) {
        if (this.endpoints != null) {
            return this.endpoints;
        }
        List<ConduitEndpoint> list = new ArrayList<>();
        for (BlockPos pos : this.members) {
            AbstractConduitBlockEntity be = conduitBe(level, pos);
            if (be == null) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                if (this.members.contains(pos.relative(dir))) {
                    continue; // conduit-to-conduit link is graph connectivity, not an endpoint
                }
                SideMode mode = be.faceMode(dir);
                ConduitEndpoint ep = new ConduitEndpoint(pos, dir, mode);
                if (ep.isSource() || ep.isSink()) {
                    list.add(ep);
                }
            }
        }
        this.endpoints = list;
        return list;
    }

    /** Idempotent per game tick: only the first member to call this each tick does the work. */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now == this.lastTickGame) {
            return;
        }
        this.lastTickGame = now;

        List<ConduitEndpoint> eps = endpoints(level);
        if (eps.isEmpty()) {
            return;
        }
        switch (this.medium) {
            case ITEM -> tickItems(level, eps);
            case FLUID -> tickFluids(level, eps);
            case ENERGY -> tickEnergy(level, eps);
        }
    }

    private void tickItems(Level level, List<ConduitEndpoint> eps) {
        int budget = NeroLogisticsConfig.itemTransferPerTick();
        final int startBudget = budget;
        List<ConduitEndpoint> sinks = sinks(eps);
        if (sinks.isEmpty()) {
            return;
        }
        for (ConduitEndpoint src : eps) {
            if (budget <= 0) {
                break;
            }
            if (!src.isSource()) {
                continue;
            }
            Container from = InventoryTransfer.containerAt(level, src.neighborPos());
            AbstractConduitBlockEntity srcBe = conduitBe(level, src.conduitPos());
            if (from == null || srcBe == null) {
                continue;
            }
            Direction fromSide = src.neighborSide();
            int slot = -1;
            while (budget > 0 && (slot = InventoryTransfer.firstExtractableSlot(from, fromSide, slot)) >= 0) {
                ItemStack stack = from.getItem(slot);
                if (stack.isEmpty() || !srcBe.itemPasses(src.face(), stack)) {
                    continue;
                }
                int want = Math.min(stack.getCount(), budget);
                int movedTotal = 0;
                for (ConduitEndpoint sink : sinks) {
                    if (budget <= 0 || movedTotal >= want) {
                        break;
                    }
                    if (sink.neighborPos().equals(src.neighborPos())) {
                        continue;
                    }
                    Container to = InventoryTransfer.containerAt(level, sink.neighborPos());
                    AbstractConduitBlockEntity sinkBe = conduitBe(level, sink.conduitPos());
                    if (to == null || sinkBe == null || !sinkBe.itemPasses(sink.face(), stack)) {
                        continue;
                    }
                    int inserted = InventoryTransfer.insert(to, sink.neighborSide(), stack, want - movedTotal);
                    movedTotal += inserted;
                    budget -= inserted;
                }
                if (movedTotal > 0) {
                    stack.shrink(movedTotal);
                    from.setChanged();
                }
            }
        }
        LogisticsMetrics.recordItems(level, (long) startBudget - budget);
    }

    private void tickEnergy(Level level, List<ConduitEndpoint> eps) {
        long budget = NeroLogisticsConfig.energyTransferPerTick();
        final long startBudget = budget;
        List<ConduitEndpoint> sinks = sinks(eps);
        if (sinks.isEmpty()) {
            return;
        }
        for (ConduitEndpoint src : eps) {
            if (budget <= 0) {
                break;
            }
            if (!src.isSource()) {
                continue;
            }
            NeroEnergyStorage from = EnergyLookup.INSTANCE.find(level, src.neighborPos(), src.neighborSide());
            if (from == null || !from.canExtract()) {
                continue;
            }
            for (ConduitEndpoint sink : sinks) {
                if (budget <= 0) {
                    break;
                }
                if (sink.neighborPos().equals(src.neighborPos())) {
                    continue;
                }
                NeroEnergyStorage to = EnergyLookup.INSTANCE.find(level, sink.neighborPos(), sink.neighborSide());
                if (to == null) {
                    continue;
                }
                long canExtract = from.extract(budget, true);
                if (canExtract <= 0) {
                    break;
                }
                long accepted = to.insert(canExtract, true);
                if (accepted <= 0) {
                    continue;
                }
                long moved = to.insert(accepted, false);
                from.extract(moved, false);
                budget -= moved;
            }
        }
        LogisticsMetrics.recordEnergy(level, startBudget - budget);
    }

    private void tickFluids(Level level, List<ConduitEndpoint> eps) {
        long budget = NeroLogisticsConfig.fluidTransferPerTick();
        final long startBudget = budget;
        List<ConduitEndpoint> sinks = sinks(eps);
        if (sinks.isEmpty()) {
            return;
        }
        for (ConduitEndpoint src : eps) {
            if (budget <= 0) {
                break;
            }
            if (!src.isSource()) {
                continue;
            }
            NeroFluidStorage from = FluidLookup.INSTANCE.find(level, src.neighborPos(), src.neighborSide());
            AbstractConduitBlockEntity srcBe = conduitBe(level, src.conduitPos());
            if (from == null || srcBe == null) {
                continue;
            }
            Fluid fluid = from.getFluid();
            if (fluid == Fluids.EMPTY || from.getAmount() <= 0 || !srcBe.fluidPasses(src.face(), fluid)) {
                continue;
            }
            for (ConduitEndpoint sink : sinks) {
                if (budget <= 0) {
                    break;
                }
                if (sink.neighborPos().equals(src.neighborPos())) {
                    continue;
                }
                NeroFluidStorage to = FluidLookup.INSTANCE.find(level, sink.neighborPos(), sink.neighborSide());
                AbstractConduitBlockEntity sinkBe = conduitBe(level, sink.conduitPos());
                if (to == null || sinkBe == null || !sinkBe.fluidPasses(sink.face(), fluid)) {
                    continue;
                }
                long drainSim = from.drain(budget, true);
                if (drainSim <= 0) {
                    break;
                }
                long fillSim = to.fill(fluid, drainSim, true);
                if (fillSim <= 0) {
                    continue;
                }
                long filled = to.fill(fluid, fillSim, false);
                long drained = from.drain(filled, false);
                budget -= drained;
            }
        }
        LogisticsMetrics.recordFluid(level, startBudget - budget);
    }

    private static List<ConduitEndpoint> sinks(List<ConduitEndpoint> eps) {
        List<ConduitEndpoint> sinks = new ArrayList<>();
        for (ConduitEndpoint ep : eps) {
            if (ep.isSink()) {
                sinks.add(ep);
            }
        }
        return sinks;
    }

    private static AbstractConduitBlockEntity conduitBe(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof AbstractConduitBlockEntity conduit ? conduit : null;
    }
}
