package za.co.neroland.nerologistics.conduit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.registry.ModBlocks;

/**
 * Network Controller: the single brain of a NeroLogistics network. Conduits, storage and ports all
 * work standalone without one, but attaching a controller to a conduit line <em>manages</em> the
 * network it touches — its modular capacity scales the network's per-tick throughput budget.
 *
 * <p><b>One controller per network.</b> Resolution is lazy and stateless: each {@link ConduitNetwork}
 * finds the single controller adjacent to its conduits; if two distinct controllers touch the same
 * network the network refuses to be managed (falls back to base throughput) and both controllers
 * report {@link Status#CONFLICT}. Players run multiple networks by bridging them with drones, not by
 * wiring two controllers into one line.
 *
 * <p><b>Modular capacity.</b> Capacity grows as the player builds: a bounded flood-fill counts the
 * {@link ModBlocks#NETWORK_MODULE} blocks connected to the controller. The throughput multiplier is
 * {@code (100 + modules*bonus%)} capped by config, and only applies while the controller is powered
 * (Core energy from cables); an unpowered controller still manages its network, but at base speed.
 *
 * <p>RF/NE power comes for free by extending {@link AbstractTerminalBlockEntity} (every loader already
 * wires that base to Core's energy capability). The controller holds no inventory (buffer size 0).
 */
public class NetworkControllerBlockEntity extends AbstractTerminalBlockEntity {

    /** Coarse managed-state of a controller, for player feedback. */
    public enum Status {
        IDLE,      // not attached to any conduit network
        ACTIVE,    // sole controller of at least one adjacent network
        CONFLICT   // shares a network with another controller — neither manages it
    }

    public static final int ENERGY_CAPACITY = 400_000;
    public static final int ENERGY_MAX_IO = 8_000;

    private static final int REFRESH_INTERVAL = 20; // ticks between capacity/status refreshes

    private int moduleCount;
    private double capacityMultiplier = 1.0;
    private Status status = Status.IDLE;

    public NetworkControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_CONTROLLER.get(), pos, state, 0, ENERGY_CAPACITY, ENERGY_MAX_IO);
    }

    /** Live throughput multiplier this controller grants the networks it manages ({@code >= 1.0}). */
    public double capacityMultiplier() {
        return this.capacityMultiplier;
    }

    public int moduleCount() {
        return this.moduleCount;
    }

    public Status status() {
        return this.status;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Modules", this.moduleCount);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.moduleCount = input.getIntOr("Modules", 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NetworkControllerBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!NeroLogisticsConfig.enableController()) {
            be.capacityMultiplier = 1.0;
            be.status = Status.IDLE;
            return;
        }
        // Draw a small upkeep each tick; whether powered gates the capacity boost (not management).
        boolean powered = be.energyBuffer().getAmount() >= NeroLogisticsConfig.controllerUpkeepPerTick();
        if (powered) {
            be.energyBuffer().consume(NeroLogisticsConfig.controllerUpkeepPerTick());
        }
        if (level.getGameTime() % REFRESH_INTERVAL == 0L) {
            be.refresh(serverLevel, pos, powered);
        }
    }

    /** Recompute module count, capacity multiplier and managed status. Cheap, runs on an interval. */
    private void refresh(ServerLevel level, BlockPos pos, boolean powered) {
        this.moduleCount = countModules(level, pos);
        int bonus = NeroLogisticsConfig.controllerModuleBonusPercent();
        int percent = Math.min(100 + this.moduleCount * bonus, NeroLogisticsConfig.controllerMaxPercent());
        this.capacityMultiplier = powered ? percent / 100.0 : 1.0;
        this.status = resolveStatus(level, pos);
        setChanged();
    }

    /** Bounded flood-fill of {@link ModBlocks#NETWORK_MODULE} blocks connected to the controller. */
    private static int countModules(ServerLevel level, BlockPos controller) {
        int max = NeroLogisticsConfig.controllerMaxModules();
        if (max <= 0) {
            return 0;
        }
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> stack = new ArrayDeque<>();
        for (Direction dir : Direction.values()) {
            stack.push(controller.relative(dir));
        }
        int count = 0;
        while (!stack.isEmpty() && count < max) {
            BlockPos p = stack.pop();
            if (!seen.add(p.immutable())) {
                continue;
            }
            if (!level.getBlockState(p).is(ModBlocks.NETWORK_MODULE.get())) {
                continue;
            }
            count++;
            for (Direction dir : Direction.values()) {
                BlockPos np = p.relative(dir);
                if (!seen.contains(np)) {
                    stack.push(np);
                }
            }
        }
        return count;
    }

    /**
     * IDLE if no adjacent conduit network; CONFLICT if any adjacent network already resolves to a
     * different controller (or flags a conflict); otherwise ACTIVE.
     */
    private Status resolveStatus(ServerLevel level, BlockPos pos) {
        boolean managesAny = false;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (!(be instanceof AbstractConduitBlockEntity conduit)) {
                continue;
            }
            NetworkMedium medium = conduit.medium();
            ConduitNetwork net = NetworkManager.networkAt(level, medium, neighbor);
            if (net == null) {
                continue;
            }
            net.refreshControllers(level);
            if (net.controllerConflict() || (net.controllerPos() != null && !pos.equals(net.controllerPos()))) {
                return Status.CONFLICT;
            }
            if (pos.equals(net.controllerPos())) {
                managesAny = true;
            }
        }
        return managesAny ? Status.ACTIVE : Status.IDLE;
    }

    // The controller holds no inventory: refuse all item interaction on its (empty) container faces.

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }
}
