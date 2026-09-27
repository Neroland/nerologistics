package za.co.neroland.nerologistics.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Retry backoff after a refused launch. */
class LaunchBackoffTest {

    @Test
    @DisplayName("doubles from the interval and caps at the configured maximum")
    void doublesAndCaps() {
        assertEquals(100, LaunchBackoff.delayTicks(0, 100, 2_400));
        assertEquals(200, LaunchBackoff.delayTicks(1, 100, 2_400));
        assertEquals(1_600, LaunchBackoff.delayTicks(4, 100, 2_400));
        assertEquals(2_400, LaunchBackoff.delayTicks(5, 100, 2_400));
        assertEquals(2_400, LaunchBackoff.delayTicks(LaunchBackoff.MAX_LEVEL, 100, 2_400));
    }

    @Test
    @DisplayName("never overflows, never goes below the interval, tolerates bad input")
    void edges() {
        assertEquals(1_728_000, LaunchBackoff.delayTicks(1_000, 72_000, 1_728_000));
        assertEquals(100, LaunchBackoff.delayTicks(3, 100, 10)); // a cap below the base is lifted to it
        assertEquals(1, LaunchBackoff.delayTicks(-4, 0, 0));
        assertEquals(LaunchBackoff.MAX_LEVEL, LaunchBackoff.nextLevel(LaunchBackoff.MAX_LEVEL));
        assertEquals(1, LaunchBackoff.nextLevel(-7));
    }
}
