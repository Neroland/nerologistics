package za.co.neroland.nerologistics.link;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkAlerts;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.ship.ShippingClass;

/**
 * The write half of the link module, and a deliberately small one: two actions,
 * {@code set_shipping_class} and {@code acknowledge_alert}.
 *
 * <h2>Why these two and no others</h2>
 *
 * <p>Almost everything a companion client might want to do to a logistics network moves items.
 * Extracting from the storage index, pushing a stack into a buffer, running the auto-crafter, firing a
 * cargo port early, retargeting a drone port mid-delivery — all of them change quantities of things in
 * a world the player is not standing in, and several of them would let a phone empty a drive bay
 * remotely. None of that is here, and none of it should be.
 *
 * <p><b>Cancelling a shipment in flight is deliberately absent.</b> A manifest holds the only copy of
 * its payload for the duration of transit; "cancel" would have to either destroy that payload or
 * materialise it somewhere, and both are item movement dressed up as a status toggle.
 *
 * <p>What is left is a port's <b>quality-of-service lane</b>. Flipping STANDARD / EXPRESS / BULK
 * changes no quantity of anything: on the built-in route it re-prices the <em>next</em> launch's fuel
 * and re-times its transit; under Nerospace routing (where travel time and fuel are Nerospace's) it
 * re-prices the port's launch energy and makes BULK wait for a full load. The same tap flips it back. It is the one genuinely safe write this mod has, and it is
 * the one a player actually wants away from the keyboard ("get that batch here fast, I'll pay the
 * fuel"). Acknowledging your own alert touches nothing but your own notification list.
 *
 * <h2>Validation</h2>
 *
 * <p>Server-authoritative, and the incoming {@link UUID} is trusted for <em>nothing beyond scoping the
 * request to the requester's own surroundings</em>:
 *
 * <ol>
 *   <li>the player must be <b>online</b> ({@link LinkActionResult.Error#PLAYER_OFFLINE_REQUIRED});</li>
 *   <li>the named block must be in the player's own dimension and inside
 *       {@link NeroLogisticsLinkAccess#SCAN_RADIUS} blocks of them — the same reach rule the snapshots
 *       use, so the app can only touch a port it was allowed to show. Out of reach reads exactly like
 *       "there is no port there" ({@link LinkActionResult.Error#NOT_OWNER}), so the action cannot be
 *       used to probe the world for cargo ports;</li>
 *   <li>if the port <b>records an owner</b> — which happens only on worlds that opted into
 *       {@code perPlayerThroughputAttribution} — that owner must be the requester
 *       ({@link LinkActionResult.Error#NOT_OWNER}). On a default world no port has an owner and reach
 *       is the whole gate, which is exactly the in-game permission: anyone standing there can already
 *       cycle the class with a Configurator. This action is therefore strictly narrower than what the
 *       player can do by walking over, never wider;</li>
 *   <li>{@code class}, if supplied, must name a real {@link ShippingClass}
 *       ({@link LinkActionResult.Error#VALIDATION}); omitted, the port cycles.</li>
 *   <li>with {@code enableShippingQos=false} the action is refused
 *       ({@link LinkActionResult.Error#ACTION_DISABLED}), exactly as the in-game Configurator refuses
 *       to cycle the class.</li>
 * </ol>
 *
 * <p><b>Privacy (POPIA/GDPR).</b> No result names any player and no owner UUID is ever read out — the
 * ownership gate asks {@code isOwnedBy} rather than fetching the UUID. A result carries a position the
 * requester supplied, a class name and a couple of booleans.
 *
 * <p>Server thread only.
 */
public final class NeroLogisticsLinkActions implements LinkActionHandler {

    private static final List<String> ACTIONS = List.of(
            NeroLogisticsLinkModule.ACTION_SET_SHIPPING_CLASS,
            NeroLogisticsLinkModule.ACTION_ACKNOWLEDGE_ALERT);

    @Override
    public String moduleId() {
        return NeroLogisticsLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return ACTIONS;
    }

    /**
     * Honestly, per action. Acknowledging your own alert is a notification-list operation and works
     * perfectly well while you are away — that is rather the point of an alert. Retuning a cargo port
     * is a change to the world, and this module's only permission model is "you are standing near it",
     * which cannot be evaluated for a player who is not standing anywhere.
     */
    @Override
    public boolean allowOffline(String actionId) {
        return NeroLogisticsLinkModule.ACTION_ACKNOWLEDGE_ALERT.equals(actionId);
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        if (playerId == null) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION, "No player was supplied.");
        }
        if (!NeroLogisticsLinkAccess.enabled()) {
            return LinkActionResult.error(LinkActionResult.Error.ACTION_DISABLED,
                    "The NeroLogistics link module is disabled on this server.");
        }
        MinecraftServer server = NeroLogisticsLinkAccess.server();
        if (server == null) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL,
                    "The server is not running a world yet.");
        }
        try {
            if (NeroLogisticsLinkModule.ACTION_SET_SHIPPING_CLASS.equals(actionId)) {
                return setShippingClass(server, playerId, params);
            }
            if (NeroLogisticsLinkModule.ACTION_ACKNOWLEDGE_ALERT.equals(actionId)) {
                return acknowledgeAlert(server, playerId, params);
            }
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "NeroLogistics does not know the action '" + actionId + "'.");
        } catch (RuntimeException e) {
            // Action id only — never who asked (POPIA/GDPR).
            NeroLogisticsCommon.LOGGER.warn("[NeroLogistics] NeroLink action '{}' failed.", actionId, e);
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL,
                    "The action could not be processed.");
        }
    }

    // --- set_shipping_class ---------------------------------------------------

    private static LinkActionResult setShippingClass(MinecraftServer server, UUID playerId,
            JsonObject params) {
        NeroLogisticsLinkAccess.Reach reach = NeroLogisticsLinkAccess.reach(server, playerId);
        if (reach == null) {
            return LinkActionResult.error(LinkActionResult.Error.PLAYER_OFFLINE_REQUIRED,
                    "A cargo port's shipping class can only be changed while you are online and near it.");
        }
        Integer x = NeroLogisticsLinkAccess.integer(params, "x");
        Integer y = NeroLogisticsLinkAccess.integer(params, "y");
        Integer z = NeroLogisticsLinkAccess.integer(params, "z");
        if (x == null || y == null || z == null) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "The 'x', 'y' and 'z' parameters must name a cargo port's position.");
        }
        BlockPos pos = new BlockPos(x, y, z);
        ServerLevel level = reach.level();
        // Fails CLOSED and identically for "too far", "not loaded" and "not a cargo port", so the
        // action cannot be used to probe the world.
        if (!reach.covers(pos) || !level.isLoaded(pos)
                || !(level.getBlockEntity(pos) instanceof RocketCargoPortBlockEntity port)) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER,
                    "There is no cargo port you can reach at that position.");
        }
        if (port.hasOwner() && !port.isOwnedBy(playerId)) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER,
                    "That cargo port belongs to someone else.");
        }
        if (!NeroLogisticsConfig.enableShippingQos()) {
            // Same rule as the in-game Configurator: no lanes on this server, so nothing to change.
            return LinkActionResult.error(LinkActionResult.Error.ACTION_DISABLED,
                    "Shipping classes are disabled on this server.");
        }

        String requested = NeroLogisticsLinkAccess.string(params, "class");
        ShippingClass applied;
        if (requested == null) {
            applied = port.cycleShippingClass();
        } else {
            ShippingClass wanted = parse(requested);
            if (wanted == null) {
                return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                        "The 'class' parameter must be STANDARD, EXPRESS or BULK.");
            }
            applied = port.setShippingClass(wanted);
        }

        JsonObject result = new JsonObject();
        result.addProperty("schema_version", NeroLogisticsLinkModule.SCHEMA_VERSION);
        result.add("position", NeroLogisticsLinkAccess.position(pos));
        result.addProperty("shipping_class", applied.name());
        result.addProperty("effective_shipping_class", port.effectiveShippingClass().name());
        result.addProperty("qos_enabled", NeroLogisticsConfig.enableShippingQos());
        return LinkActionResult.ok(result);
    }

    /** Strict parse — {@code ShippingClass.byName} falls back to STANDARD, which would hide a typo. */
    private static ShippingClass parse(String raw) {
        String wanted = raw.trim().toUpperCase(Locale.ROOT);
        for (ShippingClass value : ShippingClass.values()) {
            if (value.name().equals(wanted)) {
                return value;
            }
        }
        return null;
    }

    // --- acknowledge_alert ----------------------------------------------------

    /**
     * Marks one of the caller's own NeroLogistics alerts as read in Core's shared alert store. The
     * store is per-player by construction, so this can only ever reach the caller's own row.
     */
    private static LinkActionResult acknowledgeAlert(MinecraftServer server, UUID playerId,
            JsonObject params) {
        String alertId = NeroLogisticsLinkAccess.string(params, "alert");
        if (alertId == null) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "The 'alert' parameter must be an alert id.");
        }
        if (!LinkAlerts.get(server).ack(server, playerId, alertId)) {
            return LinkActionResult.error(LinkActionResult.Error.VALIDATION,
                    "You have no unacknowledged alert with that id.");
        }
        JsonObject result = new JsonObject();
        result.addProperty("schema_version", NeroLogisticsLinkModule.SCHEMA_VERSION);
        result.addProperty("alert", alertId);
        result.addProperty("acked", true);
        return LinkActionResult.ok(result);
    }
}
