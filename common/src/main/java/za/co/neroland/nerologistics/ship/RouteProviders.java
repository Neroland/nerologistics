package za.co.neroland.nerologistics.ship;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * Holds the route providers. The {@link StubRouteProvider} is always present; {@code compat.NerospaceCompat}
 * binds a Nerospace provider when Nerospace (with its route API) is loaded. {@link #get()} picks the
 * Nerospace one only while the server config {@code nerospaceRouting} allows it, so a pack can keep the
 * stub even with Nerospace installed — read per call, so a server-synced config change applies at once.
 */
public final class RouteProviders {

    private static final StubRouteProvider STUB = new StubRouteProvider();

    @Nullable
    private static volatile RouteProvider nerospace;

    private RouteProviders() {
    }

    /** The provider ports launch through right now. */
    public static RouteProvider get() {
        RouteProvider bound = nerospace;
        return bound != null && NeroLogisticsConfig.nerospaceRouting() ? bound : STUB;
    }

    /** The stub, regardless of what is bound (legacy {@code DestIndex} migration resolves against it). */
    public static StubRouteProvider stub() {
        return STUB;
    }

    /** Whether a Nerospace provider is bound (Nerospace present with a compatible API). */
    public static boolean nerospaceBound() {
        return nerospace != null;
    }

    /** Bind the Nerospace provider (called once from {@code NerospaceCompat.init()}). */
    public static void bindNerospace(RouteProvider provider) {
        if (provider != null) {
            nerospace = provider;
        }
    }

    /** Tick every provider — a bound Nerospace provider keeps draining its events even while unselected. */
    public static void tickAll(MinecraftServer server) {
        STUB.tick(server);
        RouteProvider bound = nerospace;
        if (bound != null) {
            bound.tick(server);
        }
    }

    /** Server-stopped reset of every provider's in-memory caches. */
    public static void resetAll() {
        STUB.reset();
        RouteProvider bound = nerospace;
        if (bound != null) {
            bound.reset();
        }
    }
}
