package za.co.neroland.nerologistics.progression;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

/**
 * In-memory "who last configured this block" table behind {@link OperatorCredit}. Keys are held
 * weakly, so an unloaded block entity drops out on its own; nothing here is ever saved or logged.
 * Bounded by {@link #MAX_ENTRIES}: past the cap new operators are simply not noted (no credit, no harm).
 */
final class OperatorLedger<K> {

    static final int MAX_ENTRIES = 4096;

    private final Map<K, UUID> operators = new WeakHashMap<>();

    void note(K key, @Nullable UUID player) {
        if (player == null) {
            return;
        }
        if (this.operators.size() >= MAX_ENTRIES && !this.operators.containsKey(key)) {
            return;
        }
        this.operators.put(key, player);
    }

    /** Remove and return the operator noted for {@code key}, or {@code null}. */
    @Nullable
    UUID take(K key) {
        return this.operators.remove(key);
    }

    void forget(UUID player) {
        this.operators.values().removeIf(player::equals);
    }

    void clear() {
        this.operators.clear();
    }

    int size() {
        return this.operators.size();
    }
}
