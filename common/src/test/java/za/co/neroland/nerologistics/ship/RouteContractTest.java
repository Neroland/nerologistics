package za.co.neroland.nerologistics.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerospace.api.route.FlightRequestResult;
import za.co.neroland.nerospace.api.route.FlightState;
import za.co.neroland.nerospace.api.route.ScheduleMode;

/**
 * NeroLogistics mirrors a few Nerospace enums by name so nothing outside {@code compat.nerospace} touches a
 * Nerospace type. These tests fail the build if Nerospace adds a constant this build would silently map to
 * a fallback, so the mirror is updated in the same change as the Nerospace pin.
 */
class RouteContractTest {

    @Test
    @DisplayName("every Nerospace denial has a named ShipDenial")
    void denials() {
        for (FlightRequestResult.Denial denial : FlightRequestResult.Denial.values()) {
            ShipDenial mapped = ShipDenial.byName(denial.name());
            assertNotEquals(ShipDenial.OTHER, mapped, denial.name());
            assertEquals(denial.name(), mapped.name());
        }
        assertEquals(ShipDenial.OTHER, ShipDenial.byName("SOMETHING_NEW"));
        assertEquals(ShipDenial.OTHER, ShipDenial.byName(null));
    }

    @Test
    @DisplayName("every Nerospace flight state has a FlightView state")
    void states() {
        for (FlightState state : FlightState.values()) {
            assertEquals(state.name(), FlightView.State.byName(state.name()).name());
        }
        assertEquals(FlightView.State.IN_FLIGHT, FlightView.State.byName("WARPING")); // unknown = still live
    }

    @Test
    @DisplayName("port schedules mirror Nerospace's pad schedule modes")
    void schedules() {
        assertEquals(ScheduleMode.values().length, PortSchedule.values().length);
        assertEquals(PortSchedule.WHEN_FULL, PortSchedule.byName(ScheduleMode.WHEN_FULL.name()));
        assertEquals(PortSchedule.MANUAL, PortSchedule.byName(ScheduleMode.MANUAL.name()));
        assertEquals(PortSchedule.EVERY_INTERVAL, PortSchedule.byName(ScheduleMode.EVERY_INTERVAL.name()));
        assertEquals(PortSchedule.EVERY_INTERVAL, PortSchedule.byName(null));
        assertEquals(PortSchedule.EVERY_INTERVAL, PortSchedule.MANUAL.next());
    }

    @Test
    @DisplayName("ETA rounds up to whole seconds and never goes negative")
    void eta() {
        assertEquals(0, new FlightView(1, "a", "b", FlightView.State.IN_FLIGHT, -40, -1).etaSeconds());
        assertEquals(1, new FlightView(1, "a", "b", FlightView.State.IN_FLIGHT, 1, -1).etaSeconds());
        assertEquals(3, new FlightView(1, "a", "b", FlightView.State.IN_FLIGHT, 60, -1).etaSeconds());
    }
}
