package za.co.neroland.nerologistics.link;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.network.ConduitEndpoint;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;

/**
 * The one place NeroLogistics' link-module visibility rule lives, plus the plumbing every surface
 * needs: the running server handle, the online lookup, the proximity scan, and null-safe JSON
 * parameter readers.
 *
 * <h2>The visibility rule, in exactly one place</h2>
 *
 * <p>{@link #reach(MinecraftServer, UUID)} is the whole of it. NeroLogistics records no owner for a
 * network, a drive bay, a drone port, a drone or a manifest — that state is world state, not player
 * state — so there is nothing to filter by. Inventing an owner would be a fiction, and answering with
 * the server-wide registries would hand a companion client a map of every base on the server.
 *
 * <p>So a request sees what the requester could already see by standing where they are: their own
 * dimension, within {@link #SCAN_RADIUS} blocks of them, while they are online. Offline it sees
 * nothing. Operator status is deliberately not honoured — an operator's powers are a property of a
 * live command source, not of a UUID arriving over a bridge, and honouring them here would turn
 * "I am an admin" into "my phone can read every base on the server".
 *
 * <p>Server thread only. Nothing here loads a chunk to answer a question.
 */
final class NeroLogisticsLinkAccess {

    /**
     * How far, in blocks, every section looks around an online player. Chosen to comfortably cover a
     * base and its outbuildings without reaching a neighbour's — and deliberately larger than the
     * default {@code dronePortRange} is short, so a port's whole delivery loop tends to be visible or
     * not visible together.
     */
    static final int SCAN_RADIUS = 128;

    /** Hard cap on networks reported in one snapshot, so a megabase cannot produce an unbounded page. */
    static final int MAX_NETWORKS = 32;

    /** Hard cap on blocks (bays, ports) reported per section. */
    static final int MAX_BLOCKS = 64;

    /**
     * Budget on {@code distSqr} checks spent deciding which networks are in reach. A network's member
     * set is capped at {@code maxNodesPerNetwork} but there may be many networks; this bounds the whole
     * sweep regardless. Exceeding it simply truncates the list — the envelope says so via
     * {@code truncated}.
     */
    private static final int PROXIMITY_BUDGET = 200_000;

    /**
     * The running server, captured from each loader's server-tick hook. Volatile — written from the
     * server thread, read from whichever thread Core's bridge dispatches on. Re-written every tick, so
     * it self-corrects when a new world is loaded.
     */
    @Nullable
    private static volatile MinecraftServer server;

    private NeroLogisticsLinkAccess() {
    }

    /** Captures the running server. Called once per server tick from every loader entry point. */
    static void rememberServer(MinecraftServer runningServer) {
        server = runningServer;
    }

    /** The running server, or {@code null} before the first world load. */
    @Nullable
    static MinecraftServer server() {
        return server;
    }

    /** Whether the module is switched on right now (re-read every call, so a config reload takes effect). */
    static boolean enabled() {
        try {
            return NeroLogisticsConfig.linkModuleEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The requester's live player handle, or {@code null} when they are offline. */
    @Nullable
    static ServerPlayer online(@Nullable MinecraftServer runningServer, @Nullable UUID playerId) {
        if (runningServer == null || playerId == null) {
            return null;
        }
        return runningServer.getPlayerList().getPlayer(playerId);
    }

    /** Whether the requester is online. Reported in every envelope so a client can say why a section is empty. */
    static boolean isOnline(@Nullable MinecraftServer runningServer, @Nullable UUID playerId) {
        return online(runningServer, playerId) != null;
    }

    /**
     * THE visibility rule: where the requester is standing, or {@code null} when they are offline or
     * their level is not a server level. Every section starts here and answers nothing if it is null.
     */
    @Nullable
    static Reach reach(@Nullable MinecraftServer runningServer, @Nullable UUID playerId) {
        ServerPlayer player = online(runningServer, playerId);
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        return new Reach(player, level, player.blockPosition());
    }

    /** Where the requester is, and therefore what they may be told about. */
    record Reach(ServerPlayer player, ServerLevel level, BlockPos origin) {

        /** Whether {@code pos} is inside the requester's scan radius. */
        boolean covers(BlockPos pos) {
            return pos.closerThan(this.origin, SCAN_RADIUS);
        }
    }

    /**
     * The conduit networks with at least one conduit inside the requester's reach, in medium order and
     * capped at {@link #MAX_NETWORKS}. Bounded by {@link #PROXIMITY_BUDGET} distance checks in total.
     *
     * <p>Note there is no GAS medium: NeroLogistics routes {@code ITEM}, {@code FLUID} and
     * {@code ENERGY} only ({@code NetworkMedium}), so any client tab for gas has nothing to bind to.
     */
    static List<ConduitNetwork> networksInReach(Reach reach) {
        Set<ConduitNetwork> found = new LinkedHashSet<>();
        int[] budget = {PROXIMITY_BUDGET};
        for (NetworkMedium medium : NetworkMedium.values()) {
            for (ConduitNetwork network : NetworkManager.networksIn(reach.level(), medium)) {
                if (found.size() >= MAX_NETWORKS || budget[0] <= 0) {
                    break;
                }
                if (touchesReach(network, reach, budget)) {
                    found.add(network);
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Whether any member conduit of {@code network} is in reach. Early-exits on the first hit. */
    private static boolean touchesReach(ConduitNetwork network, Reach reach, int[] budget) {
        for (BlockPos member : network.members()) {
            if (budget[0]-- <= 0) {
                return false;
            }
            if (reach.covers(member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The distinct external blocks a network touches, in endpoint order and de-duplicated. Reuses the
     * network's own cached endpoint list — the same one transport and the storage index ride — so this
     * costs no world walk of its own.
     */
    static List<BlockPos> neighboursOf(ConduitNetwork network, ServerLevel level) {
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (ConduitEndpoint endpoint : network.endpoints(level)) {
            seen.add(endpoint.neighborPos());
        }
        return new ArrayList<>(seen);
    }

    // --- JSON helpers ---------------------------------------------------------

    /** A string parameter, or {@code null} when absent / not a primitive. Never throws. */
    @Nullable
    static String string(@Nullable JsonObject params, String key) {
        if (params == null || !params.has(key)) {
            return null;
        }
        JsonElement element = params.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isEmpty() ? null : value;
    }

    /** An integer parameter, or {@code null} when absent / not a number. Never throws. */
    @Nullable
    static Integer integer(@Nullable JsonObject params, String key) {
        if (params == null || !params.has(key)) {
            return null;
        }
        JsonElement element = params.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A bounded integer query parameter, clamped into {@code [min, max]}; unparseable falls back. */
    static int boundedQuery(@Nullable java.util.Map<String, String> params, String key,
            int fallback, int min, int max) {
        String raw = params == null ? null : params.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A lower-cased free-text query parameter, or {@code null} when absent/blank. */
    @Nullable
    static String query(@Nullable java.util.Map<String, String> params, String key) {
        String raw = params == null ? null : params.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** A block position as three fields — used only for blocks already inside the requester's reach. */
    static JsonObject position(BlockPos pos) {
        JsonObject at = new JsonObject();
        at.addProperty("x", pos.getX());
        at.addProperty("y", pos.getY());
        at.addProperty("z", pos.getZ());
        return at;
    }

    /** A display label from a registry path segment: {@code "drive_bay"} becomes {@code "Drive bay"}. */
    static String readablePath(String path) {
        String cleaned = path.replace('_', ' ').trim();
        if (cleaned.isEmpty()) {
            return path;
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }
}
