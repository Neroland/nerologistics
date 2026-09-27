package za.co.neroland.nerologistics.ship;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.world.SavedDataRecovery;

/**
 * Durable {@link SavedData} store for every pending/in-flight shipment (rocket cargo + train hauls).
 * Attached to the <b>overworld's</b> data storage (always loaded, mirrors NeroTech's
 * {@code PollutionState}), loaded lazily on first access after server start and flushed with the
 * world save — so a shipment in transit survives a restart and its timer resumes, because
 * {@link CargoManifest#arrivalTick()} is absolute overworld game time, which persists too.
 *
 * <p>POPIA/GDPR: shipments carry payload, dimensions, positions and ticks only — no player names,
 * no UUIDs, nothing to erase (opt-in launch attribution lives separately in {@code LogisticsMetrics},
 * which is wired to Core's {@code PlayerDataErasure}).
 */
public final class ShipmentState extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "shipments");

    public static final SavedDataType<ShipmentState> TYPE =
            new SavedDataType<>(ID, ShipmentState::new, codec(), null);

    private final List<CargoManifest> shipments = new ArrayList<>();

    public ShipmentState() {
    }

    /** The instance last fetched for {@link #cachedFor} (the storage never swaps it after load). */
    @org.jetbrains.annotations.Nullable
    private static ShipmentState cached;
    @org.jetbrains.annotations.Nullable
    private static MinecraftServer cachedFor;

    public static ShipmentState get(MinecraftServer server) {
        // Guarded load: a corrupt/truncated data file recovers via backup-then-fresh instead of
        // crashing the per-tick shipment driver (mirrors Nerospace's SavedDataRecovery pattern).
        ShipmentState state = SavedDataRecovery.get(server.overworld(), TYPE, ShipmentState::new, ID.toString());
        cached = state;
        cachedFor = server;
        return state;
    }

    /**
     * The per-tick accessor: the instance cached for this server, or a full {@link #get} on first use /
     * after a server change. Skips the recovery helper's backup bookkeeping, so callers still run
     * {@link #get} periodically (the shipment driver does, once a second).
     */
    public static ShipmentState cached(MinecraftServer server) {
        ShipmentState state = cached;
        return state != null && cachedFor == server ? state : get(server);
    }

    /** Forget the cached instance (server-stopped reset). */
    public static void clearCache() {
        cached = null;
        cachedFor = null;
    }

    /** Number of shipments currently pending/in flight. */
    public int count() {
        return this.shipments.size();
    }

    /** Read-only copy of the in-transit shipments, oldest first (dashboard / command listing). */
    public List<CargoManifest> snapshot() {
        return List.copyOf(this.shipments);
    }

    /** Whether a shipment launched from {@code dim}/{@code pos} is still in transit (processor gate). */
    public boolean hasShipmentFrom(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
            net.minecraft.core.BlockPos pos) {
        for (CargoManifest manifest : this.shipments) {
            if (manifest.fromPos().equals(pos) && manifest.fromDim().equals(dim)) {
                return true;
            }
        }
        return false;
    }

    /** Queue a shipment. Caller enforces the {@code maxPendingShipments} cap before launching. */
    public void add(CargoManifest manifest) {
        this.shipments.add(manifest);
        setDirty();
    }

    /** Remove and return every shipment matching {@code due}, oldest first. */
    public List<CargoManifest> drainDue(Predicate<CargoManifest> due) {
        List<CargoManifest> out = new ArrayList<>();
        Iterator<CargoManifest> it = this.shipments.iterator();
        while (it.hasNext()) {
            CargoManifest manifest = it.next();
            if (due.test(manifest)) {
                out.add(manifest);
                it.remove();
            }
        }
        if (!out.isEmpty()) {
            setDirty();
        }
        return out;
    }

    // --- persistence --------------------------------------------------------

    private static Codec<ShipmentState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                CargoManifest.CODEC.listOf().optionalFieldOf("shipments", List.of())
                        .forGetter(s -> List.copyOf(s.shipments))
        ).apply(inst, ShipmentState::fromData));
    }

    private static ShipmentState fromData(List<CargoManifest> shipments) {
        ShipmentState state = new ShipmentState();
        state.shipments.addAll(shipments);
        return state;
    }
}
