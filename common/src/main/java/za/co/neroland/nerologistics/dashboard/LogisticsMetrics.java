package za.co.neroland.nerologistics.dashboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * Logistics telemetry powering the dashboard.
 *
 * <p><b>Aggregate counters are world data, not player data</b> — keyed by dimension, never by player —
 * and are always collected. <b>Per-player attribution is opt-in and default OFF</b>
 * ({@code perPlayerThroughputAttribution}); with it off, NeroLogistics stores no personal data at all.
 * When on, it records only the placing player's <b>UUID</b> (never a name), is retention-pruned
 * ({@code attributionRetentionDays}), and is purged through Core's shared data-erasure hook
 * ({@code data.PlayerDataErasure}) — both on request and by the inactivity sweep. (POPIA/GDPR.)
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
    }

    private static final Map<ResourceKey<Level>, Counters> COUNTERS = new HashMap<>();
    /** UUID -> {lastActiveEpochDay, shipmentCount}. Only populated when attribution is opted in. */
    private static final Map<UUID, long[]> ATTRIBUTION = new HashMap<>();
    private static long lastPruneDay = -1L;

    private LogisticsMetrics() {
    }

    private static Counters counters(ResourceKey<Level> dim) {
        return COUNTERS.computeIfAbsent(dim, k -> new Counters());
    }

    /** Read-only snapshot for the dashboard. */
    public static Counters countersOf(ResourceKey<Level> dim) {
        return counters(dim);
    }

    public static void recordItems(Level level, long amount) {
        if (!level.isClientSide() && amount > 0) {
            counters(level.dimension()).itemsMoved += amount;
        }
    }

    public static void recordFluid(Level level, long amount) {
        if (!level.isClientSide() && amount > 0) {
            counters(level.dimension()).fluidMoved += amount;
        }
    }

    public static void recordEnergy(Level level, long amount) {
        if (!level.isClientSide() && amount > 0) {
            counters(level.dimension()).energyMoved += amount;
        }
    }

    public static void recordDrone(Level level) {
        if (!level.isClientSide()) {
            counters(level.dimension()).dronesDispatched++;
        }
    }

    public static void recordShipmentLaunched(Level level) {
        if (!level.isClientSide()) {
            counters(level.dimension()).shipmentsLaunched++;
        }
    }

    public static void recordShipmentDelivered(Level level) {
        if (!level.isClientSide()) {
            counters(level.dimension()).shipmentsDelivered++;
        }
    }

    /** Opt-in attribution of a shipment to its port's owner. No-op unless attribution is enabled. */
    public static void recordPlayerShipment(MinecraftServer server, @Nullable UUID owner) {
        if (owner == null || !NeroLogisticsConfig.perPlayerThroughputAttribution()) {
            return;
        }
        long[] record = ATTRIBUTION.computeIfAbsent(owner, u -> new long[] {today(), 0L});
        record[0] = today();
        record[1]++;
    }

    /** POPIA/GDPR erasure: remove everything stored for {@code player}. Registered with Core. */
    public static void erasePlayer(MinecraftServer server, UUID player) {
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
}
