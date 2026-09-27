package za.co.neroland.nerologistics.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lane maths that do not depend on live config values. */
class ShippingClassTest {

    @Test
    @DisplayName("percentage scaling rounds up, keeps a charge of at least 1, and keeps free free")
    void scaleUp() {
        assertEquals(10_000, ShippingClass.scaleUp(10_000, 100));
        assertEquals(30_000, ShippingClass.scaleUp(10_000, 300));
        assertEquals(5_000, ShippingClass.scaleUp(10_000, 50));
        assertEquals(2, ShippingClass.scaleUp(3, 50)); // 1.5 rounds up
        assertEquals(1, ShippingClass.scaleUp(1, 1)); // never below 1 while anything is charged
        assertEquals(0, ShippingClass.scaleUp(0, 300)); // free stays free
        assertEquals(0, ShippingClass.scaleUp(-5, 300));
        assertEquals(Integer.MAX_VALUE, ShippingClass.scaleUp(Integer.MAX_VALUE, 10_000)); // no overflow
    }

    @Test
    @DisplayName("Nerospace launch phases: express first, then standard, then bulk, inside the interval")
    void launchPhase() {
        assertEquals(0, ShippingClass.EXPRESS.launchPhase(100));
        assertEquals(1, ShippingClass.STANDARD.launchPhase(100));
        assertEquals(2, ShippingClass.BULK.launchPhase(100));
        assertTrue(ShippingClass.EXPRESS.launchPhase(100) < ShippingClass.STANDARD.launchPhase(100));
        // Tiny intervals collapse onto the valid phases rather than never firing.
        assertEquals(0, ShippingClass.BULK.launchPhase(1));
        assertEquals(1, ShippingClass.BULK.launchPhase(2));
    }

    @Test
    @DisplayName("cycle and persisted names")
    void cycleAndNames() {
        assertEquals(ShippingClass.EXPRESS, ShippingClass.STANDARD.next());
        assertEquals(ShippingClass.BULK, ShippingClass.EXPRESS.next());
        assertEquals(ShippingClass.STANDARD, ShippingClass.BULK.next());
        assertEquals(ShippingClass.BULK, ShippingClass.byName("bulk"));
        assertEquals(ShippingClass.STANDARD, ShippingClass.byName(null));
        assertEquals(ShippingClass.STANDARD, ShippingClass.byName("warp"));
    }
}
