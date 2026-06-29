package za.co.neroland.nerologistics.ship;

/**
 * Holds the active {@link RouteProvider}. Defaults to {@link StubRouteProvider}; a Nerospace-aware
 * integration calls {@link #set} during init to swap in a planet/station-backed provider. This is the
 * single seam that lets cross-dimension shipping light up against {@code nerospace.api} without
 * NeroLogistics taking a compile-time Nerospace dependency.
 */
public final class RouteProviders {

    private static volatile RouteProvider current = new StubRouteProvider();

    private RouteProviders() {
    }

    public static RouteProvider get() {
        return current;
    }

    public static void set(RouteProvider provider) {
        if (provider != null) {
            current = provider;
        }
    }
}
