package za.co.neroland.nerologistics.ship;

import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * The cross-dimension routing seam. Stage 4 codes against this interface and a {@link StubRouteProvider}
 * so shipping is testable without Nerospace; when Nerospace is present, the
 * {@code compat.NerospaceCompat} hook swaps in a {@code nerospace.api}-backed provider via
 * {@link RouteProviders#set} — NeroLogistics never imports Nerospace (the binding is reflective), so it
 * stays inert (not broken) when Nerospace is absent.
 *
 * <p>The {@code from}-aware overloads exist because real routes (Nerospace's planet catalog) price and
 * time a shipment per origin/destination pair; the defaults ignore {@code from} so the stub keeps its
 * flat every-dimension behaviour.
 */
public interface RouteProvider {

    /** The destinations reachable on this server. */
    List<RouteDestination> destinations(MinecraftServer server);

    /** Whether {@code destination} is currently reachable (its dimension is loaded). */
    default boolean isAvailable(MinecraftServer server, RouteDestination destination) {
        return server.getLevel(destination.dimension()) != null;
    }

    /** Whether the {@code from} → {@code destination} route is currently open. */
    default boolean isAvailable(MinecraftServer server, ResourceKey<Level> from, RouteDestination destination) {
        return isAvailable(server, destination);
    }

    /** Transit time in ticks for a shipment to {@code destination}. */
    default int transitTicks(MinecraftServer server, RouteDestination destination) {
        return NeroLogisticsConfig.shipTransitTicks();
    }

    /** Transit time in ticks for a shipment from {@code from} to {@code destination}. */
    default int transitTicks(MinecraftServer server, ResourceKey<Level> from, RouteDestination destination) {
        return transitTicks(server, destination);
    }

    /**
     * Rocket-fuel-tagged items ({@code nerologistics:rocket_fuel}) consumed to launch from {@code from}
     * to {@code destination}; {@code 0} means launches are free (the server config disabled fuel).
     */
    default int fuelPerLaunch(MinecraftServer server, ResourceKey<Level> from, RouteDestination destination) {
        return NeroLogisticsConfig.shipFuelPerLaunch();
    }
}
