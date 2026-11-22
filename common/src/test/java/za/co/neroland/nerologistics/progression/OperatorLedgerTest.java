package za.co.neroland.nerologistics.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The in-memory operator table behind the drone and network-controller gates. */
class OperatorLedgerTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    @DisplayName("the last operator is credited once, then forgotten")
    void takeOnce() {
        OperatorLedger<String> ledger = new OperatorLedger<>();
        String hub = new String("hub");
        ledger.note(hub, ALICE);
        ledger.note(hub, BOB);
        assertEquals(BOB, ledger.take(hub));
        assertNull(ledger.take(hub));
    }

    @Test
    @DisplayName("a null player is ignored")
    void nullIgnored() {
        OperatorLedger<String> ledger = new OperatorLedger<>();
        ledger.note(new String("port"), null);
        assertEquals(0, ledger.size());
    }

    @Test
    @DisplayName("erasure removes every entry for that player only")
    void erasure() {
        OperatorLedger<String> ledger = new OperatorLedger<>();
        String a = new String("a");
        String b = new String("b");
        String c = new String("c");
        ledger.note(a, ALICE);
        ledger.note(b, ALICE);
        ledger.note(c, BOB);
        ledger.forget(ALICE);
        assertNull(ledger.take(a));
        assertNull(ledger.take(b));
        assertEquals(BOB, ledger.take(c));
    }

    @Test
    @DisplayName("the table is capped; past the cap new blocks are not noted, known ones still update")
    void capped() {
        OperatorLedger<String> ledger = new OperatorLedger<>();
        String[] keys = new String[OperatorLedger.MAX_ENTRIES];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new String("k" + i);
            ledger.note(keys[i], ALICE);
        }
        String extra = new String("extra");
        ledger.note(extra, BOB);
        assertEquals(OperatorLedger.MAX_ENTRIES, ledger.size());
        assertNull(ledger.take(extra));
        ledger.note(keys[0], BOB);
        assertEquals(BOB, ledger.take(keys[0]));
    }
}
