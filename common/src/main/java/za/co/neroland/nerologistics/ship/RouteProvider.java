package za.co.neroland.nerologistics.ship;

import java.util.List;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * The cross-dimension routing seam. Stage 4 codes against this interface and a {@link StubRouteProvider}
 * so shipping is testable without Nerospace; the Stage-0 {@code nerospace.api} promotion (planet/station
 * catalog) supplies a richer implementation that is swapped in via {@link RouteProviders#set} from a
 * Nerospace-aware compat hook — NeroLogistics never imports Nerospace, so it stays inert (not broken)
 * when Nerospace is absent.
 */
public interface RouteProvider {

    /** The destinations reachable on this server. */
    List<RouteDestination> destinations(MinecraftServer server);

    /** Whether {@code destination} is currently reachable (its dimension is loaded). */
    default boolean isAvailable(MinecraftServer server, RouteDestination destination) {
        return server.getLevel(destination.dimension()) != null;
    }

    /** Transit time in ticks for a shipment to {@code destination}. */
    default int transitTicks(MinecraftServer server, RouteDestination destination) {
        return NeroLogisticsConfig.shipTransitTicks();
    }
}
