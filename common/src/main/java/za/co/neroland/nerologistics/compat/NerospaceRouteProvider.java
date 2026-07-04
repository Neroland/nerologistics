package za.co.neroland.nerologistics.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.ship.RouteDestination;
import za.co.neroland.nerologistics.ship.RouteProvider;

/**
 * {@link RouteProvider} backed by Nerospace's semver-stable {@code za.co.neroland.nerospace.api}
 * (the {@code NerospaceRoutes} static facade). Bound <b>reflectively</b> so NeroLogistics keeps zero
 * compile-time Nerospace dependency — the six build cells stay green with or without Nerospace on the
 * classpath, matching the manifests' {@code optional}/{@code suggests} posture. Only the {@code Method}
 * handles are cached; every shipment queries a <b>fresh</b> route, because Nerospace computes
 * {@code fuelCostMb} at query time from its own (config-scaled) values.
 *
 * <p>Mapping:
 * <ul>
 *   <li>{@code endpoints()} → {@link RouteDestination}{@code (endpoint.dimension(), endpoint.name())}
 *       — the static planet/station catalog (Home, station, Greenxertz, Cindara, Glacira);</li>
 *   <li>{@code isAvailable(from, dest)} → {@code NerospaceRoutes.isOpen(server, from, dest)} — false
 *       for non-endpoints and {@code from == dest} (a rocket to your own dimension makes no sense);</li>
 *   <li>{@code transitTicks(from, dest)} → {@code route(from, dest).transitTicks()} (1 200 ticks per
 *       dimension-separation step), falling back to {@code shipTransitTicks()} config;</li>
 *   <li>{@code fuelPerLaunch(from, dest)} → {@code route(from, dest).fuelCostMb()} converted at
 *       1 000 mB per rocket-fuel-tagged item (rounded up, min the {@code shipFuelPerLaunch} config,
 *       capped at a stack), falling back to {@code shipFuelPerLaunch()}; a config of 0 keeps launches
 *       free.</li>
 * </ul>
 *
 * <p>Any reflective failure logs once and degrades to the interface defaults — never a crash.
 */
public final class NerospaceRouteProvider implements RouteProvider {

    private static final String API_PACKAGE = "za.co.neroland.nerospace.api.";
    /** One rocket-fuel-tagged item is worth this many mB of Nerospace fuel. */
    private static final int MB_PER_FUEL_ITEM = 1_000;
    /** A launch never needs more fuel items than one buffer slot holds. */
    private static final int MAX_FUEL_ITEMS = 64;

    private final Method endpoints;         // static List<RouteEndpoint> NerospaceRoutes.endpoints()
    private final Method isOpen;            // static boolean NerospaceRoutes.isOpen(server, from, to)
    private final Method route;             // static Optional<CargoRoute> NerospaceRoutes.route(from, to)
    private final Method endpointDimension; // ResourceKey<Level> RouteEndpoint.dimension()
    private final Method endpointName;      // String RouteEndpoint.name()
    private final Method routeTransitTicks; // int CargoRoute.transitTicks()
    private final Method routeFuelCostMb;   // int CargoRoute.fuelCostMb()

    private final AtomicBoolean warned = new AtomicBoolean();

    private NerospaceRouteProvider(Method endpoints, Method isOpen, Method route, Method endpointDimension,
            Method endpointName, Method routeTransitTicks, Method routeFuelCostMb) {
        this.endpoints = endpoints;
        this.isOpen = isOpen;
        this.route = route;
        this.endpointDimension = endpointDimension;
        this.endpointName = endpointName;
        this.routeTransitTicks = routeTransitTicks;
        this.routeFuelCostMb = routeFuelCostMb;
    }

    /** Resolve the {@code nerospace.api} surface. Throws if Nerospace (or this API level) is absent. */
    static NerospaceRouteProvider create() throws ReflectiveOperationException {
        ClassLoader loader = NerospaceRouteProvider.class.getClassLoader();
        Class<?> routes = Class.forName(API_PACKAGE + "NerospaceRoutes", false, loader);
        Class<?> endpoint = Class.forName(API_PACKAGE + "RouteEndpoint", false, loader);
        Class<?> cargoRoute = Class.forName(API_PACKAGE + "CargoRoute", false, loader);
        return new NerospaceRouteProvider(
                routes.getMethod("endpoints"),
                routes.getMethod("isOpen", MinecraftServer.class, ResourceKey.class, ResourceKey.class),
                routes.getMethod("route", ResourceKey.class, ResourceKey.class),
                endpoint.getMethod("dimension"),
                endpoint.getMethod("name"),
                cargoRoute.getMethod("transitTicks"),
                cargoRoute.getMethod("fuelCostMb"));
    }

    @Override
    public List<RouteDestination> destinations(MinecraftServer server) {
        try {
            List<?> eps = (List<?>) this.endpoints.invoke(null);
            List<RouteDestination> out = new ArrayList<>(eps.size());
            for (Object ep : eps) {
                @SuppressWarnings("unchecked")
                ResourceKey<Level> dimension = (ResourceKey<Level>) this.endpointDimension.invoke(ep);
                out.add(new RouteDestination(dimension, (String) this.endpointName.invoke(ep)));
            }
            return List.copyOf(out);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("endpoints", e);
            return List.of();
        }
    }

    @Override
    public boolean isAvailable(MinecraftServer server, ResourceKey<Level> from, RouteDestination destination) {
        try {
            return (Boolean) this.isOpen.invoke(null, server, from, destination.dimension());
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("isOpen", e);
            return server.getLevel(destination.dimension()) != null;
        }
    }

    @Override
    public int transitTicks(MinecraftServer server, ResourceKey<Level> from, RouteDestination destination) {
        try {
            Optional<?> cargoRoute = (Optional<?>) this.route.invoke(null, from, destination.dimension());
            if (cargoRoute.isPresent()) {
                return (Integer) this.routeTransitTicks.invoke(cargoRoute.get());
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("route/transitTicks", e);
        }
        return NeroLogisticsConfig.shipTransitTicks();
    }

    @Override
    public int fuelPerLaunch(MinecraftServer server, ResourceKey<Level> from, RouteDestination destination) {
        int base = NeroLogisticsConfig.shipFuelPerLaunch();
        if (base <= 0) {
            return 0; // the server made launches fuel-free; a Nerospace route must not override that
        }
        try {
            Optional<?> cargoRoute = (Optional<?>) this.route.invoke(null, from, destination.dimension());
            if (cargoRoute.isPresent()) {
                int fuelCostMb = (Integer) this.routeFuelCostMb.invoke(cargoRoute.get());
                if (fuelCostMb > 0) {
                    int items = (fuelCostMb + MB_PER_FUEL_ITEM - 1) / MB_PER_FUEL_ITEM;
                    return Math.min(MAX_FUEL_ITEMS, Math.max(base, items));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("route/fuelCostMb", e);
        }
        return base;
    }

    private void warnOnce(String call, Exception e) {
        if (this.warned.compareAndSet(false, true)) {
            NeroLogisticsCommon.LOGGER.warn(
                    "[NeroLogistics] Nerospace route API call '{}' failed ({}); falling back to stub "
                    + "behaviour for the failing calls", call, e.toString());
        }
    }
}
