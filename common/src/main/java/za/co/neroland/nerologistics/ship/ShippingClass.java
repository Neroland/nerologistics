package za.co.neroland.nerologistics.ship;

import java.util.Locale;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * Quality-of-service lane for a rocket cargo port. A port's class trades transit time against fuel
 * cost, applied where the manifest's departure/arrival ticks and the launch's fuel bill are computed
 * (never stored in the manifest — a {@link CargoManifest} saved before this feature loads unchanged,
 * and a port saved without a class defaults to {@link #STANDARD}):
 *
 * <ul>
 *   <li><b>STANDARD</b> — the route's base transit and fuel, unchanged.</li>
 *   <li><b>EXPRESS</b> — much faster ({@code expressTransitFactor}% of base, min 20 ticks) but
 *       fuel-hungry ({@code expressFuelFactor}% of base).</li>
 *   <li><b>BULK</b> — slower ({@code bulkTransitFactor}% of base) but cheap
 *       ({@code bulkFuelFactor}% of base, rounded up, never below 1 while the route charges any).</li>
 * </ul>
 *
 * <p>With {@code enableShippingQos=false} every port behaves as {@link #STANDARD} (clean degrade);
 * the configured class is kept in NBT so re-enabling the toggle restores it.</p>
 */
public enum ShippingClass {

    STANDARD,
    EXPRESS,
    BULK;

    /** Transit ticks after this class's factor; EXPRESS is floored at 20 ticks, everything at 1. */
    public int applyTransit(int baseTicks) {
        return switch (this) {
            case STANDARD -> baseTicks;
            case EXPRESS -> Math.max(20,
                    (int) Math.max(1L, (long) baseTicks * NeroLogisticsConfig.expressTransitFactor() / 100L));
            case BULK -> (int) Math.min(Integer.MAX_VALUE,
                    Math.max(1L, (long) baseTicks * NeroLogisticsConfig.bulkTransitFactor() / 100L));
        };
    }

    /**
     * Fuel items after this class's factor, rounded <em>up</em> and never below 1 while the route
     * charges any fuel at all; a fuel-free route ({@code base <= 0}) stays free in every class.
     */
    public int applyFuel(int baseFuel) {
        if (baseFuel <= 0) {
            return 0;
        }
        long percent = switch (this) {
            case STANDARD -> 100L;
            case EXPRESS -> NeroLogisticsConfig.expressFuelFactor();
            case BULK -> NeroLogisticsConfig.bulkFuelFactor();
        };
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (baseFuel * percent + 99L) / 100L));
    }

    /** The next class in the STANDARD → EXPRESS → BULK cycle. */
    public ShippingClass next() {
        ShippingClass[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Parse a persisted name; unknown or missing names fall back to {@link #STANDARD}. */
    public static ShippingClass byName(String name) {
        for (ShippingClass value : values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return STANDARD;
    }

    /** Lang key for the player-facing class name. */
    public String translationKey() {
        return "block.nerologistics.rocket_cargo_port.shipping_class." + name().toLowerCase(Locale.ROOT);
    }
}
