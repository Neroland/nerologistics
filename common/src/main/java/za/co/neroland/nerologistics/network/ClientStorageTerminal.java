package za.co.neroland.nerologistics.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

/**
 * Client-side mailbox for {@link StorageTerminalContentsPayload}, keyed by container id — the same
 * split Nerotech uses for its menu-scoped payloads ({@code ClientMenuPos} et al.): the clientbound
 * handler stays free of client-only classes and just drops the payload here; the storage terminal
 * screen polls it each container tick and applies it to its menu once the container ids match, so
 * a stale payload can never repopulate a different menu. Revisions are compared by the menu, so an
 * out-of-order payload for the same container id loses.
 *
 * <p>Bounded by vanilla's container-id counter (wraps at 100 per session) and drained on poll.
 * Holds only container ids and block-scoped inventory aggregates — no player data (POPIA/GDPR).
 */
public final class ClientStorageTerminal {

    private static final Map<Integer, StorageTerminalContentsPayload> PENDING = new ConcurrentHashMap<>();

    private ClientStorageTerminal() {
    }

    /** Called by the clientbound handler. */
    public static void accept(StorageTerminalContentsPayload payload) {
        PENDING.put(payload.containerId(), payload);
    }

    /** Take and clear the pending contents for a container id, or {@code null} if none. */
    @Nullable
    public static StorageTerminalContentsPayload poll(int containerId) {
        return PENDING.remove(containerId);
    }

    public static void clear() {
        PENDING.clear();
    }
}
