package za.co.neroland.nerologistics.ship;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * A read-only line for the dashboard / {@code /nerologistics shipping}: one shipment in transit, in
 * NeroLogistics terms, so nothing outside {@code compat.nerospace} ever sees a Nerospace type. Labels are
 * pad names or dimension paths; never a player name, UUID or another player's pad position.
 *
 * @param id          flight id (Nerospace) or queue position (stub)
 * @param origin      origin label
 * @param destination destination label
 * @param state       lifecycle state
 * @param etaTicks    ticks until the timer elapses ({@code 0} once due)
 * @param heldTicks   ticks spent holding, or {@code -1} when not holding
 */
public record FlightView(int id, String origin, String destination, State state, long etaTicks, long heldTicks) {

    /** Mirrors Nerospace's {@code FlightState}; the stub only ever reports {@link #IN_FLIGHT}. */
    public enum State {
        IN_FLIGHT,
        AWAITING_CHUNK,
        HOLDING,
        UNLOADING,
        DELIVERED,
        DROPPED;

        public String translationKey() {
            return "nerologistics.ship.state." + name().toLowerCase(Locale.ROOT);
        }

        /** Safe lookup by name; unknown names read as {@link #IN_FLIGHT} ("still live"). */
        public static State byName(String name) {
            if (name != null) {
                for (State value : values()) {
                    if (value.name().equalsIgnoreCase(name)) {
                        return value;
                    }
                }
            }
            return IN_FLIGHT;
        }
    }

    /** ETA rounded up to whole seconds. */
    public long etaSeconds() {
        return (Math.max(0L, this.etaTicks) + 19L) / 20L;
    }

    /** One chat line: {@code #12 Home Pad → Station: Alpha — in flight, ETA 42 s}. */
    public Component describe() {
        Component stateText = Component.translatable(this.state.translationKey());
        if (this.state == State.HOLDING && this.heldTicks >= 0) {
            return Component.translatable("nerologistics.ship.flight.held", this.id, this.origin, this.destination,
                    stateText, (this.heldTicks + 19L) / 20L);
        }
        if (this.state == State.IN_FLIGHT) {
            return Component.translatable("nerologistics.ship.flight.eta", this.id, this.origin, this.destination,
                    stateText, etaSeconds());
        }
        return Component.translatable("nerologistics.ship.flight", this.id, this.origin, this.destination, stateText);
    }
}
