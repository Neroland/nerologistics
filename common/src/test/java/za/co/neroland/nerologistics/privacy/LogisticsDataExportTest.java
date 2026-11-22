package za.co.neroland.nerologistics.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;

/** NeroLogistics' part of Core's data export. Plain JVM. */
class LogisticsDataExportTest {

    @Test
    void nothingHeldMeansNull() {
        assertNull(LogisticsDataExport.build(null, false, false, false, false, null, List.of()));
    }

    @Test
    void anOptOutAloneIsExported() {
        JsonObject doc = LogisticsDataExport.build(null, true, false, false, false, null, List.of());
        assertNotNull(doc);
        assertTrue(doc.getAsJsonObject("shipment_attribution").get("opted_out").getAsBoolean());
        assertFalse(doc.getAsJsonObject("shipment_attribution").has("shipments_attributed"));
    }

    @Test
    void holdsAttributionPerksAndPortRoles() {
        RocketCargoPortBlockEntity.PortRole port = new RocketCargoPortBlockEntity.PortRole(
                Identifier.fromNamespaceAndPath("minecraft", "overworld"), new BlockPos(1, 64, 2), false, true);
        JsonObject doc = LogisticsDataExport.build(new long[] {20_000L, 7L}, false, true, true, false, null,
                List.of(port));
        assertNotNull(doc);
        assertEquals(7L, doc.getAsJsonObject("shipment_attribution").get("shipments_attributed").getAsLong());
        assertEquals("2024-10-04", doc.getAsJsonObject("shipment_attribution").get("last_active").getAsString());
        assertTrue(doc.getAsJsonObject("quest_perks").get("dashboard_badge").getAsBoolean());
        JsonObject row = doc.getAsJsonArray("rocket_cargo_ports_loaded_now").get(0).getAsJsonObject();
        assertTrue(row.get("you_are_dispatcher").getAsBoolean());
        assertFalse(row.get("you_are_owner").getAsBoolean());
    }
}
