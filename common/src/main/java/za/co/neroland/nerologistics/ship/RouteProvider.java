package za.co.neroland.nerologistics.ship;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * The rocket-cargo routing seam. Two implementations fit it: the {@link StubRouteProvider} (every loaded
 * dimension is a destination; NeroLogistics simulates the flight in {@link ShipmentState}) and the
 * Nerospace provider in {@code compat.nerospace} (destinations are Cargo Pads and stations; the flight is
 * a real Nerospace flight). The port, dashboard and commands only ever see this interface and the
 * NeroLogistics value types ({@link RouteDestination}, {@link LaunchResult}, {@link FlightView}), so
 * no Nerospace class is loaded when Nerospace is absent.
 */
public interface RouteProvider {

    /** Short id for logs ({@code stub} / {@code nerospace}). */
    String id();

    /**
     * Whether the port pays for launches with its own {@code nerologistics:rocket_fuel}-tagged items.
     * {@code false} for Nerospace: the docked rocket carries the fuel, so the port must not charge twice.
     */
    boolean chargesOwnFuel();

    /** Whether launches act on behalf of a player (the port's dispatcher). */
    boolean needsDispatcher();

    /** Destinations {@code origin} may target, in a stable order, never including the origin itself. */
    List<RouteDestination> destinations(MinecraftServer server, ShipOrigin origin);

    /** Resolve a saved key back to a live destination, or empty when it is gone or not visible. */
    Optional<RouteDestination> resolve(MinecraftServer server, ShipOrigin origin, DestinationKey key);

    /** Why {@code origin} cannot launch at all right now (before any cargo is considered), or null. */
    @Nullable
    default ShipDenial originProblem(MinecraftServer server, ShipOrigin origin) {
        return null;
    }

    /** Player-facing label of the origin endpoint (the Cargo Pad's name), when the provider has one. */
    default Optional<String> originName(MinecraftServer server, ShipOrigin origin) {
        return Optional.empty();
    }

    /** Fuel items the port must burn for this launch ({@link #chargesOwnFuel()} providers only). */
    default int fuelItems(MinecraftServer server, ShipOrigin origin, RouteDestination destination) {
        return 0;
    }

    /** Estimated travel time in ticks for a status line, or empty when the provider cannot quote. */
    default Optional<Integer> estimateTicks(MinecraftServer server, ShipOrigin origin, RouteDestination destination,
            List<ItemStack> payload, boolean returnEmpty) {
        return Optional.empty();
    }

    /**
     * Launch {@code payload} now. On {@link LaunchResult#isAccepted()} the provider owns the cargo and the
     * port removes it from its buffer; on a denial the port keeps it.
     */
    LaunchResult launch(MinecraftServer server, ShipOrigin origin, RouteDestination destination,
            List<ItemStack> payload, ShippingClass qos, boolean returnEmpty);

    /** Shipments of this provider currently in transit. */
    int inTransitCount(MinecraftServer server);

    /** Whether ports must stop launching (the {@code maxPendingShipments} cap). */
    default boolean atCapacity(MinecraftServer server) {
        return inTransitCount(server) >= NeroLogisticsConfig.maxPendingShipments();
    }

    /**
     * In-transit shipments launched from within {@code radius} blocks of {@code centre} in {@code dim} —
     * proximity-scoped like the link module, never a server-wide roster.
     */
    List<FlightView> flightsNear(MinecraftServer server, ResourceKey<Level> dim, BlockPos centre, int radius);

    /**
     * Recently crated (dropped) shipments launched near {@code centre}, as ready-made hint lines
     * ({@code flight id → destination, and the crate position when {@code viewer} may know it}). The viewer
     * UUID is used for that visibility check only and never stored. Empty for the stub, which never crates.
     */
    default List<DropHint> recentDropsNear(MinecraftServer server, ResourceKey<Level> dim, BlockPos centre,
            int radius, @Nullable java.util.UUID viewer) {
        return List.of();
    }

    /**
     * One crated shipment for the dashboard.
     *
     * @param flightId    the flight id
     * @param destination destination label, with the crate position appended only when the viewer may know it
     * @param ageTicks    ticks since the cargo was crated
     */
    record DropHint(int flightId, String destination, long ageTicks) {
    }

    /** Per-server-tick housekeeping (event drain, reconciliation). Must be cheap when idle. */
    default void tick(MinecraftServer server) {
    }

    /** Drop in-memory caches; called from the server-stopped reset. */
    default void reset() {
    }
}
