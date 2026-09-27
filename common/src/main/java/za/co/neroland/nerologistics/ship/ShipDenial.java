package za.co.neroland.nerologistics.ship;

import java.util.Locale;

/**
 * Why a rocket cargo port did not launch on its last attempt. Recorded on the port (name + tick) and
 * shown in its status line, the dashboard and {@code /nerologistics shipping}. The Nerospace-side
 * constants mirror {@code nerospace.api.route.FlightRequestResult.Denial} by name, so a new Nerospace
 * constant maps to {@link #OTHER} instead of breaking anything.
 *
 * <p>Carries no player data.</p>
 */
public enum ShipDenial {

    // --- NeroLogistics-side --------------------------------------------------
    /** No destination selected, or the saved one is gone / not visible any more. */
    NO_DESTINATION(true),
    /** The provider has no open route between origin and destination. */
    ROUTE_CLOSED(true),
    /** Stub: no same-channel port in the destination dimension. */
    NO_DESTINATION_PORT(true),
    /** Stub: not enough rocket-fuel-tagged items in the buffer. */
    NO_FUEL_ITEMS(true),
    /** Not enough energy for the per-stack charge. Retried every interval (energy arrives over cables). */
    NO_ENERGY(false),
    /** The in-transit cap ({@code maxPendingShipments}) is reached. Retried every interval. */
    AT_CAPACITY(false),
    /** Nerospace: the port is not touching a Cargo Pad its dispatcher can use. */
    NO_ORIGIN_PAD(true),
    /** Nerospace: nobody has configured the port (or its dispatcher's data was erased). */
    NO_DISPATCHER(true),

    // --- Mirrors Nerospace's FlightRequestResult.Denial -------------------------
    NO_ORIGIN(true),
    NOT_PERMITTED(true),
    ORIGIN_NOT_LOADED(true),
    NO_ROCKET(true),
    PAD_TOO_SMALL(true),
    BAD_MANIFEST(true),
    NOT_ENOUGH_FUEL(true),
    TOO_MANY_FLIGHTS(true),
    /** A denial this build does not know yet (a newer Nerospace), or an unexpected error. */
    OTHER(true);

    private final boolean backsOff;

    ShipDenial(boolean backsOff) {
        this.backsOff = backsOff;
    }

    /** Whether retries back off exponentially (vs. every interval, for transient shortages). */
    public boolean backsOff() {
        return this.backsOff;
    }

    public String translationKey() {
        return "nerologistics.ship.denial." + name().toLowerCase(Locale.ROOT);
    }

    /** Safe lookup by name (persistence, Nerospace mapping); unknown or null reads as {@link #OTHER}. */
    public static ShipDenial byName(String name) {
        if (name != null) {
            for (ShipDenial value : values()) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }
        }
        return OTHER;
    }
}
