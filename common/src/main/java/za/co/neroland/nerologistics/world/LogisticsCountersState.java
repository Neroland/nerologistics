package za.co.neroland.nerologistics.world;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;

/**
 * The dashboard's per-dimension throughput counters, persisted with the world (overworld data folder)
 * so a restart no longer resets them. Keyed by <b>dimension id</b> only — world data, never player data
 * (POPIA/GDPR: nothing here identifies a player, so there is nothing to erase). The opt-in per-player
 * attribution stays in memory in {@link LogisticsMetrics} and is untouched by this store.
 *
 * <p>Persisting the totals is what makes the world-log threshold channels ({@code
 * nerologistics:shipments_delivered}, {@code drones_dispatched}, {@code items_moved}) honest: a rung is
 * crossed once per dimension for the life of the world, not again after every restart. Read through the
 * guarded {@link SavedDataRecovery} accessor like every other NeroLogistics store.</p>
 */
public final class LogisticsCountersState extends SavedData {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "logistics_counters");

    private static final Codec<LogisticsMetrics.Counters> COUNTERS_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.optionalFieldOf("items_moved", 0L).forGetter(c -> c.itemsMoved),
            Codec.LONG.optionalFieldOf("fluid_moved", 0L).forGetter(c -> c.fluidMoved),
            Codec.LONG.optionalFieldOf("energy_moved", 0L).forGetter(c -> c.energyMoved),
            Codec.LONG.optionalFieldOf("drones_dispatched", 0L).forGetter(c -> c.dronesDispatched),
            Codec.LONG.optionalFieldOf("shipments_launched", 0L).forGetter(c -> c.shipmentsLaunched),
            Codec.LONG.optionalFieldOf("shipments_delivered", 0L).forGetter(c -> c.shipmentsDelivered),
            Codec.LONG.optionalFieldOf("shipments_held", 0L).forGetter(c -> c.shipmentsHeld),
            Codec.LONG.optionalFieldOf("shipments_dropped", 0L).forGetter(c -> c.shipmentsDropped)
    ).apply(inst, LogisticsCountersState::counters));

    public static final SavedDataType<LogisticsCountersState> TYPE =
            new SavedDataType<>(ID, LogisticsCountersState::new, codec(), null);

    /** Dimension id (e.g. {@code minecraft:overworld}) -> totals. */
    private final Map<String, LogisticsMetrics.Counters> byDimension = new HashMap<>();

    public LogisticsCountersState() {
    }

    public static LogisticsCountersState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, LogisticsCountersState::new, ID.toString());
    }

    /** The live (mutable) counters of {@code dim}, created on first use. Callers mark dirty after a change. */
    public LogisticsMetrics.Counters counters(ResourceKey<Level> dim) {
        return this.byDimension.computeIfAbsent(dim.identifier().toString(), k -> new LogisticsMetrics.Counters());
    }

    private static LogisticsMetrics.Counters counters(long items, long fluid, long energy, long drones,
            long launched, long delivered, long held, long dropped) {
        LogisticsMetrics.Counters c = new LogisticsMetrics.Counters();
        c.itemsMoved = items;
        c.fluidMoved = fluid;
        c.energyMoved = energy;
        c.dronesDispatched = drones;
        c.shipmentsLaunched = launched;
        c.shipmentsDelivered = delivered;
        c.shipmentsHeld = held;
        c.shipmentsDropped = dropped;
        return c;
    }

    private static Codec<LogisticsCountersState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Codec.unboundedMap(Codec.STRING, COUNTERS_CODEC).optionalFieldOf("dimensions", Map.of())
                        .forGetter(s -> Map.copyOf(s.byDimension))
        ).apply(inst, LogisticsCountersState::fromData));
    }

    private static LogisticsCountersState fromData(Map<String, LogisticsMetrics.Counters> dims) {
        LogisticsCountersState state = new LogisticsCountersState();
        state.byDimension.putAll(dims);
        return state;
    }
}
