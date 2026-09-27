package za.co.neroland.nerologistics.conduit;

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
import za.co.neroland.nerologistics.menu.LogisticsProcessorMenu;
import za.co.neroland.nerologistics.network.ConduitEndpoint;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShipDenial;
import za.co.neroland.nerologistics.storage.ItemKey;
import za.co.neroland.nerologistics.storage.NetworkStorageIndex;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * Logistics Processor — rule-based supply policies ("logistics programming"). Attaches to an
 * adjacent ITEM-network duct like the buffer/auto-crafter and holds up to {@value #RULE_COUNT}
 * rules. Each rule pairs a ghost item (exact item + components), a comparator (BELOW/ABOVE), a
 * threshold and an action:
 *
 * <ul>
 *   <li><b>KEEP_STOCKED_ADJACENT</b> — if the count in the processor's adjacent (non-duct)
 *       inventory satisfies the comparator, pull the deficit from the network storage index into
 *       it (a chest-side twin of the buffer's keep-stocked mode, but drawing on digital storage).</li>
 *   <li><b>EXPORT_EXCESS</b> — if the network index count satisfies the comparator, push the
 *       excess over the threshold from the index into the adjacent inventory (trash chest,
 *       outbound barrel…).</li>
 *   <li><b>SHIP_ABOVE</b> — if the network index count satisfies the comparator, extract the
 *       excess and feed it to the nearest {@link RocketCargoPortBlockEntity} on the same network;
 *       the port then launches it along its own configured destination/channel/QoS lane. With no
 *       port or no buffered fuel the rule idles with a status (never errors).</li>
 * </ul>
 *
 * <p>Each rule can also carry a {@link RuleGate} — a condition on the nearest rocket cargo port (needs
 * fuel / has a shipment in transit / is stalled). A gated rule only acts while its condition holds, and a
 * gated rule with no ghost item is an <b>alarm</b>: while any alarm holds the processor emits a full
 * redstone signal (e.g. a lamp for a stalled lane, or a fuel pump for a rocket that ran dry).</p>
 *
 * <p>Rules are evaluated server-side on a position-staggered interval
 * ({@code logisticsRuleIntervalTicks}, default 40 — never per tick), each moving at most
 * {@code logisticsActionCapPerCycle} items and charging {@code logisticsEnergyPerAction} NE per
 * executed action (energy arrives over cables, same as the auto-crafter). Rules persist in BE NBT;
 * per-rule statuses are transient and surfaced in the GUI via the menu's synced data. All state is
 * block-scoped — no player data (POPIA/GDPR).</p>
 */
public class LogisticsProcessorBlockEntity extends AbstractTerminalBlockEntity implements MenuProvider {

    public static final int RULE_COUNT = 8;
    public static final int THRESHOLD_MIN = 1;
    public static final int THRESHOLD_MAX = 1_000_000;
    public static final int ENERGY_CAPACITY = 200_000;
    public static final int ENERGY_MAX_IO = 4_000;

    /** What a rule does when its comparison holds. */
    public enum RuleAction {
        KEEP_STOCKED_ADJACENT,
        EXPORT_EXCESS,
        SHIP_ABOVE
    }

    /**
     * Optional per-rule condition on the nearest rocket cargo port of the processor's network. A rule
     * with a gate other than {@link #ALWAYS} only acts while the gate holds; with no ghost item it is an
     * <b>alarm-only</b> rule that just drives the processor's redstone output. Appended values only —
     * persisted by ordinal.
     */
    public enum RuleGate {
        /** No port condition (the pre-0.4 behaviour). */
        ALWAYS,
        /** The port's last launch was refused for fuel (rocket low on fuel, or no fuel items). */
        PORT_NEEDS_FUEL,
        /** A shipment the port launched is still in transit. */
        PORT_IN_TRANSIT,
        /** The port's last launch was refused for any reason. */
        PORT_STALLED;

        public boolean holds(RocketCargoPortBlockEntity port, net.minecraft.server.MinecraftServer server) {
            ShipDenial denial = port.lastDenial();
            return switch (this) {
                case ALWAYS -> true;
                case PORT_NEEDS_FUEL -> denial == ShipDenial.NOT_ENOUGH_FUEL || denial == ShipDenial.NO_FUEL_ITEMS;
                case PORT_IN_TRANSIT -> port.hasFlightInTransit(server);
                case PORT_STALLED -> denial != null;
            };
        }
    }

    /** Transient per-rule outcome of the last evaluation pass, surfaced as the GUI status dot. */
    public enum RuleStatus {
        /** Rule disabled or no ghost item configured. */
        DISABLED,
        /** Condition not met (or nothing to move) — healthy steady state. */
        IDLE,
        /** The last pass moved items. */
        ACTED,
        /** No ITEM conduit network is attached to the processor. */
        NO_NETWORK,
        /** No adjacent (non-duct) inventory to pull into / push out to. */
        NO_TARGET,
        /** No rocket cargo port on the network (or cross-dimension shipping disabled). */
        NO_PORT,
        /** The nearest rocket port has no rocket-fuel-tagged items buffered. */
        NO_FUEL,
        /** Not enough energy for the per-action charge. */
        NO_ENERGY,
        /** Condition met but nothing could move (network empty of the item, or the target full). */
        BLOCKED,
        /** The rule's port condition does not hold right now. */
        GATED,
        /** Alarm-only rule (no item) whose port condition holds — the processor emits redstone. */
        ALARM
    }

    /** Ghost rule items: slot i = rule i's exact item+components match (count ignored, stamped 1). */
    private final SimpleContainer ghosts = new SimpleContainer(RULE_COUNT);
    private final boolean[] above = new boolean[RULE_COUNT];
    private final int[] thresholds = new int[RULE_COUNT];
    private final RuleAction[] actions = new RuleAction[RULE_COUNT];
    private final boolean[] enabled = new boolean[RULE_COUNT];
    private final RuleGate[] gates = new RuleGate[RULE_COUNT];
    /** Redstone output: 15 while any alarm-only rule's gate holds. Transient, recomputed each pass. */
    private int signal;
    /** Transient (not persisted): last evaluation outcome per rule. */
    private final RuleStatus[] statuses = new RuleStatus[RULE_COUNT];

    public LogisticsProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_PROCESSOR.get(), pos, state, 0, ENERGY_CAPACITY, ENERGY_MAX_IO);
        for (int i = 0; i < RULE_COUNT; i++) {
            this.above[i] = false;
            this.thresholds[i] = 64;
            this.actions[i] = RuleAction.KEEP_STOCKED_ADJACENT;
            this.enabled[i] = true;
            this.gates[i] = RuleGate.ALWAYS;
            this.statuses[i] = RuleStatus.DISABLED;
        }
    }

    // --- Rule accessors (used by the menu; server-authoritative) -------------

    public SimpleContainer ghosts() {
        return this.ghosts;
    }

    public boolean ruleAbove(int rule) {
        return this.above[rule];
    }

    public int ruleThreshold(int rule) {
        return this.thresholds[rule];
    }

    public RuleAction ruleAction(int rule) {
        return this.actions[rule];
    }

    public boolean ruleEnabled(int rule) {
        return this.enabled[rule];
    }

    public RuleStatus ruleStatus(int rule) {
        return this.statuses[rule];
    }

    public RuleGate ruleGate(int rule) {
        return this.gates[rule];
    }

    /** Set a rule's port condition directly (used by the gallery's stall-alarm demo). */
    public void setRuleGate(int rule, RuleGate gate) {
        this.gates[rule] = gate;
        setChanged();
    }

    public void cycleRuleGate(int rule) {
        RuleGate[] values = RuleGate.values();
        this.gates[rule] = values[(this.gates[rule].ordinal() + 1) % values.length];
        setChanged();
    }

    /** Current redstone output (read by the block's {@code getSignal}). */
    public int signal() {
        return this.signal;
    }

    public void toggleRuleAbove(int rule) {
        this.above[rule] = !this.above[rule];
        setChanged();
    }

    public void cycleRuleAction(int rule) {
        RuleAction[] values = RuleAction.values();
        this.actions[rule] = values[(this.actions[rule].ordinal() + 1) % values.length];
        setChanged();
    }

    public void toggleRuleEnabled(int rule) {
        this.enabled[rule] = !this.enabled[rule];
        setChanged();
    }

    public void adjustRuleThreshold(int rule, int delta) {
        this.thresholds[rule] = Math.max(THRESHOLD_MIN, Math.min(THRESHOLD_MAX, this.thresholds[rule] + delta));
        setChanged();
    }

    // --- MenuProvider --------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.logistics_processor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new LogisticsProcessorMenu(id, playerInventory, this);
    }

    // --- Persistence ---------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < RULE_COUNT; i++) {
            output.store("RuleItem" + i, ItemStack.OPTIONAL_CODEC, this.ghosts.getItem(i));
            output.putInt("RuleAbove" + i, this.above[i] ? 1 : 0);
            output.putInt("RuleThr" + i, this.thresholds[i]);
            output.putInt("RuleAct" + i, this.actions[i].ordinal());
            output.putInt("RuleOn" + i, this.enabled[i] ? 1 : 0);
            output.putInt("RuleGate" + i, this.gates[i].ordinal());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        RuleAction[] actionValues = RuleAction.values();
        RuleGate[] gateValues = RuleGate.values();
        for (int i = 0; i < RULE_COUNT; i++) {
            this.ghosts.setItem(i, input.read("RuleItem" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
            this.above[i] = input.getIntOr("RuleAbove" + i, 0) != 0;
            this.thresholds[i] = Math.max(THRESHOLD_MIN,
                    Math.min(THRESHOLD_MAX, input.getIntOr("RuleThr" + i, 64)));
            int act = input.getIntOr("RuleAct" + i, 0);
            this.actions[i] = act >= 0 && act < actionValues.length ? actionValues[act]
                    : RuleAction.KEEP_STOCKED_ADJACENT;
            this.enabled[i] = input.getIntOr("RuleOn" + i, 1) != 0;
            // Rules saved before port conditions existed have no gate: ALWAYS (unchanged behaviour).
            int gate = input.getIntOr("RuleGate" + i, 0);
            this.gates[i] = gate >= 0 && gate < gateValues.length ? gateValues[gate] : RuleGate.ALWAYS;
        }
    }

    // --- Evaluation ----------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, LogisticsProcessorBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }
        if (!NeroLogisticsConfig.enableLogisticsProcessor()) {
            return;
        }
        // Position-hash phase stagger (same as buffers/auto-crafters): spreads many processors
        // across the interval instead of a thundering herd all evaluating on the same tick.
        int interval = NeroLogisticsConfig.logisticsRuleIntervalTicks();
        if (level.getGameTime() % interval != Math.floorMod(pos.hashCode(), interval)) {
            return;
        }
        be.evaluate(level, pos);
    }

    /** One evaluation pass: resolve the network + adjacent inventory once, then run each rule. */
    private void evaluate(Level level, BlockPos pos) {
        ConduitNetwork network = adjacentItemNetwork(level, pos);
        NetworkStorageIndex index = network == null ? null : network.storageIndex();
        Adjacent adjacent = findAdjacentInventory(level, pos);
        // The gate port is resolved once per pass, and only when some enabled rule has a gate.
        RocketCargoPortBlockEntity gatePort = null;
        boolean anyGate = false;
        for (int i = 0; i < RULE_COUNT; i++) {
            anyGate |= this.enabled[i] && this.gates[i] != RuleGate.ALWAYS;
        }
        if (anyGate && network != null) {
            gatePort = nearestPort(level, network);
        }
        boolean alarm = false;
        for (int i = 0; i < RULE_COUNT; i++) {
            this.statuses[i] = evaluateRule(level, i, network, index, adjacent, gatePort);
            alarm |= this.statuses[i] == RuleStatus.ALARM;
        }
        int newSignal = alarm ? 15 : 0;
        if (newSignal != this.signal) {
            this.signal = newSignal;
            level.updateNeighborsAt(pos, getBlockState().getBlock());
        }
    }

    private RuleStatus evaluateRule(Level level, int rule, @Nullable ConduitNetwork network,
            @Nullable NetworkStorageIndex index, @Nullable Adjacent adjacent,
            @Nullable RocketCargoPortBlockEntity gatePort) {
        if (!this.enabled[rule]) {
            return RuleStatus.DISABLED;
        }
        ItemStack proto = this.ghosts.getItem(rule);
        RuleGate gate = this.gates[rule];
        if (gate != RuleGate.ALWAYS) {
            if (network == null) {
                return RuleStatus.NO_NETWORK;
            }
            if (gatePort == null || level.getServer() == null) {
                return RuleStatus.NO_PORT;
            }
            if (!gate.holds(gatePort, level.getServer())) {
                return RuleStatus.GATED;
            }
            if (proto.isEmpty()) {
                return RuleStatus.ALARM; // alarm-only rule: the gate holding is the whole point
            }
        }
        if (proto.isEmpty()) {
            return RuleStatus.DISABLED;
        }
        if (network == null || index == null) {
            return RuleStatus.NO_NETWORK;
        }
        return switch (this.actions[rule]) {
            case KEEP_STOCKED_ADJACENT -> {
                if (adjacent == null) {
                    yield RuleStatus.NO_TARGET;
                }
                long have = InventoryTransfer.countExact(adjacent.container(), adjacent.side(), proto);
                if (!conditionMet(rule, have)) {
                    yield RuleStatus.IDLE;
                }
                long deficit = this.thresholds[rule] - have;
                yield moveFromIndex(level, index, proto, deficit, adjacent.container(), adjacent.side());
            }
            case EXPORT_EXCESS -> {
                if (adjacent == null) {
                    yield RuleStatus.NO_TARGET;
                }
                long netCount = networkCount(level, index, proto);
                if (!conditionMet(rule, netCount)) {
                    yield RuleStatus.IDLE;
                }
                long excess = netCount - this.thresholds[rule];
                yield moveFromIndex(level, index, proto, excess, adjacent.container(), adjacent.side());
            }
            case SHIP_ABOVE -> {
                if (!NeroLogisticsConfig.enableCrossDimension()) {
                    yield RuleStatus.NO_PORT;
                }
                RocketCargoPortBlockEntity port = nearestPort(level, network);
                if (port == null) {
                    yield RuleStatus.NO_PORT;
                }
                // Stub routes: fuel items are priced at launch; "any fuel buffered" is the idle heuristic.
                // Nerospace routes: the docked rocket carries the fuel, so the port holds none.
                if (RouteProviders.get().chargesOwnFuel() && NeroLogisticsConfig.shipFuelPerLaunch() > 0
                        && !port.hasFuelBuffered(1)) {
                    yield RuleStatus.NO_FUEL;
                }
                long netCount = networkCount(level, index, proto);
                if (!conditionMet(rule, netCount)) {
                    yield RuleStatus.IDLE;
                }
                long excess = netCount - this.thresholds[rule];
                yield moveFromIndex(level, index, proto, excess, port, Direction.UP);
            }
        };
    }

    /**
     * Extract up to {@code amount} (cap-clamped) of {@code proto} from the index into {@code dest};
     * anything the destination refuses goes straight back into the index (never voided).
     */
    private RuleStatus moveFromIndex(Level level, NetworkStorageIndex index, ItemStack proto, long amount,
            Container dest, Direction side) {
        long want = Math.min(amount, NeroLogisticsConfig.logisticsActionCapPerCycle());
        if (want <= 0) {
            return RuleStatus.IDLE;
        }
        int energyCost = NeroLogisticsConfig.logisticsEnergyPerAction();
        if (this.energy.getAmount() < energyCost) {
            return RuleStatus.NO_ENERGY;
        }
        long got = index.extractItem(level, proto, want, false);
        if (got <= 0) {
            return RuleStatus.BLOCKED; // network holds none of the item
        }
        int inserted = InventoryTransfer.insert(dest, side, proto, (int) got);
        if (inserted < got) {
            index.insertItem(level, proto, got - inserted, false); // destination full — return the rest
        }
        if (inserted <= 0) {
            return RuleStatus.BLOCKED;
        }
        this.energy.consume(energyCost);
        setChanged();
        return RuleStatus.ACTED;
    }

    private boolean conditionMet(int rule, long count) {
        return this.above[rule] ? count > this.thresholds[rule] : count < this.thresholds[rule];
    }

    private static long networkCount(Level level, NetworkStorageIndex index, ItemStack proto) {
        Long count = index.itemSnapshot(level).get(ItemKey.of(proto));
        return count == null ? 0L : count;
    }

    /**
     * The processor's adjacent inventory: the first non-conduit, non-processor {@link Container}
     * neighbour (accessed from the touching face). Ducts carry no container so mostly this just
     * skips other machines' plumbing.
     */
    @Nullable
    private Adjacent findAdjacentInventory(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof AbstractConduitBlockEntity || be instanceof LogisticsProcessorBlockEntity
                    || be instanceof RocketCargoPortBlockEntity) {
                continue;
            }
            Container container = InventoryTransfer.containerAt(level, neighbor);
            if (container != null && container != this) {
                return new Adjacent(container, dir.getOpposite());
            }
        }
        return null;
    }

    /** Nearest rocket cargo port among this network's endpoints (reuses the cached endpoint list). */
    @Nullable
    private RocketCargoPortBlockEntity nearestPort(Level level, ConduitNetwork network) {
        RocketCargoPortBlockEntity nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (ConduitEndpoint ep : network.endpoints(level)) {
            BlockPos neighbor = ep.neighborPos();
            if (level.getBlockEntity(neighbor) instanceof RocketCargoPortBlockEntity port) {
                double dist = neighbor.distSqr(this.worldPosition);
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = port;
                }
            }
        }
        return nearest;
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

    /** An adjacent external inventory and the side to access it from. */
    private record Adjacent(Container container, Direction side) {
    }
}
