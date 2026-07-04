package za.co.neroland.nerologistics.compat;

import za.co.neroland.nerolandcore.platform.Services;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.ship.RouteProviders;

/**
 * Soft-dependency seam for <b>Nerospace</b>. Called once from {@code NeroLogisticsCommon.init()}:
 * when Nerospace is loaded (checked via Core's {@code Services.PLATFORM.isModLoaded}, never a class
 * import), it swaps the default {@code StubRouteProvider} for a {@link NerospaceRouteProvider} bound
 * reflectively against the semver-stable {@code za.co.neroland.nerospace.api} surface. When Nerospace
 * is absent — or its API cannot be bound (e.g. an older Nerospace without {@code NerospaceRoutes}) —
 * the stub stays active and cross-dimension shipping keeps its standalone every-dimension behaviour.
 *
 * <p>This keeps the loader manifests' posture intact: Nerospace remains {@code optional}/{@code
 * suggests} with no compile-time dependency in any of the six cells.
 */
public final class NerospaceCompat {

    /** Nerospace's mod id, as declared optional in all three loader manifests. */
    public static final String NEROSPACE_MOD_ID = "nerospace";

    private NerospaceCompat() {
    }

    /** Bind the Nerospace route provider if Nerospace is present; otherwise leave the stub in place. */
    public static void init() {
        if (!Services.PLATFORM.isModLoaded(NEROSPACE_MOD_ID)) {
            NeroLogisticsCommon.LOGGER.info(
                    "[NeroLogistics] Nerospace absent — cross-dimension shipping uses the stub route "
                    + "provider (every loaded dimension is a destination)");
            return;
        }
        try {
            RouteProviders.set(NerospaceRouteProvider.create());
            NeroLogisticsCommon.LOGGER.info(
                    "[NeroLogistics] Nerospace detected — rocket cargo routes now come from the "
                    + "nerospace.api planet/station catalog");
        } catch (ReflectiveOperationException | RuntimeException e) {
            NeroLogisticsCommon.LOGGER.warn(
                    "[NeroLogistics] Nerospace is loaded but its route API could not be bound ({}); "
                    + "keeping the stub route provider", e.toString());
        }
    }
}
