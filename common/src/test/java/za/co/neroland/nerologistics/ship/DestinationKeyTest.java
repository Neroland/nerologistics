package za.co.neroland.nerologistics.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Stable destination identity: persistence round-trip and the pre-0.4 DestIndex migration. */
class DestinationKeyTest {

    @Test
    @DisplayName("pad and dimension keys round-trip through their persisted form")
    void roundTrip() {
        for (DestinationKey key : List.of(DestinationKey.pad(12), DestinationKey.pad(-3),
                DestinationKey.dimension("minecraft:the_nether"), DestinationKey.dimension("nerospace:greenxertz"))) {
            assertEquals(Optional.of(key), DestinationKey.parse(key.serialize()));
        }
        assertEquals("pad:-3", DestinationKey.pad(-3).serialize()); // station ids are negative
        assertEquals(Optional.of(-3), DestinationKey.pad(-3).padId());
        assertEquals(Optional.empty(), DestinationKey.dimension("minecraft:overworld").padId());
    }

    @Test
    @DisplayName("dimension keys are case-normalised")
    void dimensionCase() {
        assertEquals(DestinationKey.dimension("minecraft:the_end"), DestinationKey.dimension("Minecraft:The_End"));
    }

    @Test
    @DisplayName("malformed or unknown persisted values read as empty, never throw")
    void parseRejects() {
        for (String raw : new String[] {null, "", "pad", "pad:", ":12", "pad:abc", "pad:1.5", "foo:bar", "dim"}) {
            assertEquals(Optional.empty(), DestinationKey.parse(raw), String.valueOf(raw));
        }
        assertThrows(IllegalArgumentException.class, () -> new DestinationKey(DestinationKey.Kind.PAD, ""));
    }

    @Test
    @DisplayName("legacy DestIndex resolves like the old floorMod lookup, once")
    void legacyMigration() {
        List<DestinationKey> stub = List.of(DestinationKey.dimension("minecraft:overworld"),
                DestinationKey.dimension("minecraft:the_nether"), DestinationKey.dimension("minecraft:the_end"));
        // Old saves always wrote DestIndex (default 0).
        assertEquals(Optional.of(stub.get(0)), DestinationKey.fromLegacyIndex(0, stub));
        assertEquals(Optional.of(stub.get(2)), DestinationKey.fromLegacyIndex(2, stub));
        // An index past the end wrapped with floorMod in the old tryShip; keep that meaning.
        assertEquals(Optional.of(stub.get(1)), DestinationKey.fromLegacyIndex(4, stub));
        // Nothing to resolve against, or no legacy index at all.
        assertTrue(DestinationKey.fromLegacyIndex(1, List.of()).isEmpty());
        assertTrue(DestinationKey.fromLegacyIndex(-1, stub).isEmpty());
    }
}
