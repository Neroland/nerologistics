package za.co.neroland.nerologistics.dashboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.world.AttributionOptOutState;
import za.co.neroland.nerologistics.world.LogisticsCountersState;

/**
 * Logistics telemetry powering the dashboard.
 *
 * <p><b>Aggregate counters are world data, not player data</b> — keyed by dimension, never by player —
 * and are always collected. Since 0.6.0 they persist with the world in {@link LogisticsCountersState}
 * (previously a static map that reset on restart). <b>Per-player attribution is opt-in and default OFF</b>
 * ({@code perPlayerThroughputAttribution}); with it off, NeroLogistics stores no personal data at all.
 * When on, it records only the placing player's <b>UUID</b> (never a name), is retention-pruned
 * ({@code attributionRetentionDays}), and is purged through Core's shared data-erasure hook
 * ({@code data.PlayerDataErasure}) — both on request and by the inactivity sweep. (POPIA/GDPR.)
 *
 * <p><b>World-log channels.</b> Three persisted totals publish Neroland Core threshold crossings when a
 * dimension's total first climbs past a rung, so server-scope "world log" quests can follow them:</p>
 * <ul>
 *   <li>{@code nerologistics:shipments_delivered} — rungs 1, 10, 100, 1000</li>
 *   <li>{@code nerologistics:drones_dispatched} — rungs 100, 1000</li>
 *   <li>{@code nerologistics:items_moved} — rungs 10k, 1M, 100M</li>
 * </ul>
 * <p>The crossing's scope is the <b>dimension id</b> ({@code minecraft:overworld}) — a place, never a
 * player, UUID or block position — and each rung fires once per dimension for the life of the world.</p>
 */
public final class LogisticsMetrics {

    /** Per-dimension aggregate throughput. No player data. */
    public static final class Counters {
        public long itemsMoved;
        public long fluidMoved;
        public long energyMoved;
        public long dronesDispatched;
        public long shipmentsLaunched;
        public long shipmentsDelivered;
        /** Nerospace flights that entered HOLDING at their destination (counted once per flight). */
        public long shipmentsHeld;
        /** Nerospace flights whose cargo was crated at the destination after the hold timeout. */
        public long shipmentsDropped;
    }

    public static final Identifier CHANNEL_SHIPMENTS_DELIVERED =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "shipments_delivered");
    public static final Identifier CHANNEL_DRONES_DISPATCHED =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "drones_dispatched");
    public static final Identifier CHANNEL_ITEMS_MOVED =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "items_moved");

    private static final long[] SHIPMENT_RUNGS = {1L, 10L, 100L, 1_000L};
    private static final long[] DRONE_RUNGS = {100L, 1_000L};
    private static final long[] ITEM_RUNGS = {10_000L, 1_000_000L, 100_000_000L};

    /** UUID -> {lastActiveEpochDay, shipmentCount}. Only populated when attribution is opted in. */
    private static final Map<UUID, long[]> ATTRIBUTION = new HashMap<>();
    private static long lastPruneDay = -1L;

    /** Store instance cached for the current server tick (the guarded accessor runs once per tick at most). */
    @Nullable
    private static LogisticsCountersState cachedState;
    private static int cachedTick = -1;

    private LogisticsMetrics() {
    }

    private static LogisticsCountersState state(MinecraftServer server) {
        LogisticsCountersState state = cachedState;
        int tick = server.getTickCount();
        if (state == null || tick != cachedTick) {
            state = LogisticsCountersState.get(server);
            cachedState = state;
            cachedTick = tick;
        }
        return state;
    }

    /** Read-only snapshot for the dashboard. */
    public static Counters countersOf(MinecraftServer server, ResourceKey<Level> dim) {
        return state(server).counters(dim);
    }

    public static void recordItems(Level level, long amount) {
        MinecraftServer server = level.getServer();
        if (server == null || level.isClientSide() || amount <= 0) {
            return;
        }
        LogisticsCountersState state = state(server);
        Counters c = state.counters(level.dimension());
        long before = c.itemsMoved;
        c.itemsMoved += amount;
        state.setDirty();
        publish(CHANNEL_ITEMS_MOVED, level.dimension(), before, c.itemsMoved, ITEM_RUNGS);
    }

    public static void recordFluid(Level level, long amount) {
        MinecraftServer server = level.getServer();
        if (server == null || level.isClientSide() || amount <= 0) {
            return;
        }
        LogisticsCountersState state = state(server);
        state.counters(level.dimension()).fluidMoved += amount;
        state.setDirty();
    }

    public static void recordEnergy(Level level, long amount) {
        MinecraftServer server = level.getServer();
        if (server == null || level.isClientSide() || amount <= 0) {
            return;
        }
        LogisticsCountersState state = state(server);
        state.counters(level.dimension()).energyMoved += amount;
        state.setDirty();
    }

    public static void recordDrone(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null || level.isClientSide()) {
            return;
        }
        LogisticsCountersState state = state(server);
        Counters c = state.counters(level.dimension());
        long before = c.dronesDispatched;
        c.dronesDispatched++;
        state.setDirty();
        publish(CHANNEL_DRONES_DISPATCHED, level.dimension(), before, c.dronesDispatched, DRONE_RUNGS);
    }

    public static void recordShipmentLaunched(Level level) {
        MinecraftServer server = level.getServer();
        if (server != null && !level.isClientSide()) {
            recordShipmentLaunched(server, level.dimension());
        }
    }

    public static void recordShipmentDelivered(Level level) {
        MinecraftServer server = level.getServer();
        if (server != null && !level.isClientSide()) {
            recordShipmentDelivered(server, level.dimension());
        }
    }

    // Dimension-keyed variants for flights Nerospace reports from its own tick (server side by construction).

    public static void recordShipmentLaunched(MinecraftServer server, ResourceKey<Level> dim) {
        LogisticsCountersState state = state(server);
        state.counters(dim).shipmentsLaunched++;
        state.setDirty();
    }

    public static void recordShipmentDelivered(MinecraftServer server, ResourceKey<Level> dim) {
        LogisticsCountersState state = state(server);
        Counters c = state.counters(dim);
        long before = c.shipmentsDelivered;
        c.shipmentsDelivered++;
        state.setDirty();
        publish(CHANNEL_SHIPMENTS_DELIVERED, dim, before, c.shipmentsDelivered, SHIPMENT_RUNGS);
    }

    public static void recordShipmentHeld(MinecraftServer server, ResourceKey<Level> dim) {
        LogisticsCountersState state = state(server);
        state.counters(dim).shipmentsHeld++;
        state.setDirty();
    }

    public static void recordShipmentDropped(MinecraftServer server, ResourceKey<Level> dim) {
        LogisticsCountersState state = state(server);
        state.counters(dim).shipmentsDropped++;
        state.setDirty();
    }

    /**
     * Fire one rising crossing per rung that {@code before -> after} climbed past. The scope is the
     * dimension id — a place, never a player or a position.
     */
    private static void publish(Identifier channel, ResourceKey<Level> dim, long before, long after, long[] rungs) {
        for (long rung : rungs) {
            if (before < rung && after >= rung) {
                ThresholdEvents.fire(NeroLogisticsCommon.MOD_ID, new ThresholdEvents.ThresholdCrossing(
                        channel, dim.identifier().toString(), after, rung, true));
            }
        }
    }

    /** Opt-in attribution of a shipment to its port's owner. No-op unless attribution is enabled. */
    public static void recordPlayerShipment(MinecraftServer server, @Nullable UUID owner) {
        if (owner == null || !NeroLogisticsConfig.perPlayerThroughputAttribution()
                || AttributionOptOutState.get(server).isOptedOut(owner)) {
            return; // opted out: the shipment still counts in the per-dimension aggregates
        }
        long[] record = ATTRIBUTION.computeIfAbsent(owner, u -> new long[] {today(), 0L});
        record[0] = today();
        record[1]++;
    }

    /** POPIA/GDPR erasure: remove everything stored for {@code player}. Registered with Core. */
    public static void erasePlayer(MinecraftServer server, UUID player) {
        ATTRIBUTION.remove(player);
        AttributionOptOutState.get(server).setOptedOut(player, false);
    }

    /** In-game opt-out: drop the player's attribution record now (the aggregates are untouched). */
    public static void forgetAttribution(UUID player) {
        ATTRIBUTION.remove(player);
    }

    /** Daily retention prune of stale attribution. Cheap; runs at most once per day. */
    public static void tick(MinecraftServer server) {
        long day = today();
        if (day == lastPruneDay) {
            return;
        }
        lastPruneDay = day;
        int retention = NeroLogisticsConfig.attributionRetentionDays();
        if (retention <= 0) {
            return;
        }
        ATTRIBUTION.entrySet().removeIf(entry -> day - entry.getValue()[0] > retention);
    }

    private static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /**
     * Drop the cached store reference and all opt-in attribution. Called from the server-stopped reset
     * hook so one world's telemetry (including player-keyed attribution — POPIA/GDPR data minimisation)
     * never bleeds into the next world hosted by the same JVM. The counters themselves live in the
     * world's SavedData and are untouched.
     */
    public static void clearAll() {
        cachedState = null;
        cachedTick = -1;
        ATTRIBUTION.clear();
        lastPruneDay = -1L;
    }
}
