package za.co.neroland.nerologistics.ship;

import org.jetbrains.annotations.Nullable;

/**
 * Outcome of {@link RouteProvider#launch}: either the payload left (and the port must now remove it from
 * its buffer), or one {@link ShipDenial} says why not and the port keeps its cargo.
 *
 * @param denial   why the launch was refused, or {@code null} when accepted
 * @param flightId the provider's flight id ({@code -1} for the stub, which has none)
 */
public record LaunchResult(@Nullable ShipDenial denial, int flightId) {

    public static LaunchResult accepted(int flightId) {
        return new LaunchResult(null, flightId);
    }

    public static LaunchResult denied(ShipDenial denial) {
        return new LaunchResult(denial, -1);
    }

    public boolean isAccepted() {
        return this.denial == null;
    }
}
