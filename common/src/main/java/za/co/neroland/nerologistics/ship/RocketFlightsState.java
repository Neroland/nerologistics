package za.co.neroland.nerologistics.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.world.SavedDataRecovery;

/**
 * The Nerospace flights NeroLogistics itself requested. Nerospace persists and flies the flight; this
 * store only remembers <em>which</em> flights are ours and which port launched them, so arrival counts,
 * the dashboard's in-transit list and the per-port "flight in transit" condition stay right across a
 * restart. Also keeps a short ring of recently crated (dropped) flights for the dashboard's recovery hint.
 *
 * <p>POPIA/GDPR: flight ids, pad ids, dimensions, port positions and ticks only — no player UUID or name
 * (the dispatcher lives on the port). Bounded: at most {@code maxPendingShipments} tracked flights (the
 * port refuses to launch past it) and {@value #MAX_DROPS} drop records.</p>
 */
public final class RocketFlightsState extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "rocket_flights");

    public static final SavedDataType<RocketFlightsState> TYPE =
            new SavedDataType<>(ID, RocketFlightsState::new, codec(), null);

    /** Drop records kept for the dashboard hint (oldest evicted first). */
    public static final int MAX_DROPS = 16;

    /**
     * One flight we requested.
     *
     * @param flightId   Nerospace flight id
     * @param originDim  the launching port's dimension
     * @param originPos  the launching port's position
     * @param destPadId  destination pad / station id
     * @param launchedAt overworld game time at launch
     * @param heldSince  overworld game time the flight first held, or {@code -1}
     */
    public record Tracked(int flightId, ResourceKey<Level> originDim, BlockPos originPos, int destPadId,
            long launchedAt, long heldSince) {

        static final Codec<Tracked> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("id").forGetter(Tracked::flightId),
                Level.RESOURCE_KEY_CODEC.fieldOf("origin_dim").forGetter(Tracked::originDim),
                BlockPos.CODEC.fieldOf("origin_pos").forGetter(Tracked::originPos),
                Codec.INT.fieldOf("dest_pad").forGetter(Tracked::destPadId),
                Codec.LONG.fieldOf("launched").forGetter(Tracked::launchedAt),
                Codec.LONG.optionalFieldOf("held_since", -1L).forGetter(Tracked::heldSince)
        ).apply(inst, Tracked::new));

        Tracked held(long tick) {
            return new Tracked(this.flightId, this.originDim, this.originPos, this.destPadId, this.launchedAt, tick);
        }
    }

    /**
     * A flight whose cargo Nerospace crated at the destination.
     *
     * @param flightId  Nerospace flight id
     * @param originDim the launching port's dimension (scopes the hint to nearby viewers)
     * @param originPos the launching port's position
     * @param destPadId the destination pad / station id (its name and position are looked up live)
     * @param tick      overworld game time of the drop
     */
    public record Dropped(int flightId, ResourceKey<Level> originDim, BlockPos originPos, int destPadId, long tick) {

        static final Codec<Dropped> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("id").forGetter(Dropped::flightId),
                Level.RESOURCE_KEY_CODEC.fieldOf("origin_dim").forGetter(Dropped::originDim),
                BlockPos.CODEC.fieldOf("origin_pos").forGetter(Dropped::originPos),
                Codec.INT.fieldOf("dest_pad").forGetter(Dropped::destPadId),
                Codec.LONG.fieldOf("tick").forGetter(Dropped::tick)
        ).apply(inst, Dropped::new));
    }

    private final Map<Integer, Tracked> flights = new LinkedHashMap<>();
    private final Deque<Dropped> drops = new ArrayDeque<>();

    public RocketFlightsState() {
    }

    /** Guarded accessor (corrupt file recovers via backup-then-fresh instead of crashing the tick). */
    public static RocketFlightsState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, RocketFlightsState::new, ID.toString());
    }

    public int count() {
        return this.flights.size();
    }

    public boolean isTracked(int flightId) {
        return this.flights.containsKey(flightId);
    }

    @Nullable
    public Tracked get(int flightId) {
        return this.flights.get(flightId);
    }

    /** Snapshot of tracked flights, oldest first. */
    public List<Tracked> tracked() {
        return List.copyOf(this.flights.values());
    }

    /** Snapshot of drop records, oldest first. */
    public List<Dropped> drops() {
        return List.copyOf(this.drops);
    }

    public void track(Tracked flight) {
        this.flights.put(flight.flightId(), flight);
        setDirty();
    }

    /** Stamp the first hold; later holds of the same flight keep the original tick. */
    public void markHeld(int flightId, long tick) {
        Tracked flight = this.flights.get(flightId);
        if (flight != null && flight.heldSince() < 0) {
            this.flights.put(flightId, flight.held(tick));
            setDirty();
        }
    }

    @Nullable
    public Tracked untrack(int flightId) {
        Tracked removed = this.flights.remove(flightId);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    public void recordDrop(Dropped drop) {
        this.drops.addLast(drop);
        while (this.drops.size() > MAX_DROPS) {
            this.drops.removeFirst();
        }
        setDirty();
    }

    /** Whether a flight launched by the port at {@code dim}/{@code pos} is still live. */
    public boolean hasFlightFrom(ResourceKey<Level> dim, BlockPos pos) {
        for (Tracked flight : this.flights.values()) {
            if (flight.originPos().equals(pos) && flight.originDim().equals(dim)) {
                return true;
            }
        }
        return false;
    }

    // --- persistence --------------------------------------------------------

    private static Codec<RocketFlightsState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Tracked.CODEC.listOf().optionalFieldOf("flights", List.of()).forGetter(RocketFlightsState::tracked),
                Dropped.CODEC.listOf().optionalFieldOf("drops", List.of()).forGetter(RocketFlightsState::drops)
        ).apply(inst, RocketFlightsState::fromData));
    }

    private static RocketFlightsState fromData(List<Tracked> flights, List<Dropped> drops) {
        RocketFlightsState state = new RocketFlightsState();
        for (Tracked flight : flights) {
            state.flights.put(flight.flightId(), flight);
        }
        List<Dropped> kept = new ArrayList<>(drops);
        int skip = Math.max(0, kept.size() - MAX_DROPS);
        state.drops.addAll(kept.subList(skip, kept.size()));
        return state;
    }
}
