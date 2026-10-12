package za.co.neroland.nerologistics.link;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkAlerts;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.ship.CargoManifest;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShippingClass;

/**
 * The live half of the link module: the three things worth waking a companion client for, plus the
 * one thing worth interrupting a player for.
 *
 * <h2>Events</h2>
 *
 * <ul>
 *   <li><b>{@code shipments}</b> — a cargo port <em>you own</em> launched. Published with
 *       {@link LinkEvent#forPlayer} so the bridge routes it to that player's sessions and nobody
 *       else's, and carrying the detail an owner may see: their own port's position, the destination
 *       dimension, the payload size, the lane and the ETA.</li>
 *   <li><b>{@code shipment_state}</b> — cargo departed, or cargo landed. <b>Broadcast</b>, because a
 *       shipment is a thing that happened in a world rather than to a person.</li>
 *   <li><b>{@code network_fault}</b> — the server's in-transit queue hit its hard cap and no port can
 *       launch until deliveries drain it. <b>Broadcast</b>, because the cap is genuinely server-wide.
 *       Rate-limited to once per {@value #FAULT_COOLDOWN_MS} ms so a jammed queue with fifty ports
 *       ticking against it does not become a firehose.</li>
 * </ul>
 *
 * <h2>Alerts</h2>
 *
 * <p>One, and deliberately only one — an alert survives in Core's store until it is acknowledged, so
 * it is reserved for a thing a player would genuinely want to be told about while the game is closed:
 * <b>the shipment queue is full and your port has stopped launching</b>. Rate-limited per port, with
 * one alert id per port so a re-raise replaces rather than stacks.
 *
 * <h2>Scope and privacy (POPIA/GDPR)</h2>
 *
 * <p><b>An owner exists here far less often than the shape of this class suggests.</b> NeroLogistics
 * records an owner for exactly one kind of block — a rocket cargo port — and only on worlds that opted
 * into {@code perPlayerThroughputAttribution} (default OFF). Everywhere else there is nobody to tell,
 * so the owner-scoped topic and the alert simply never fire and the broadcast twin is the whole story.
 * That is the honest consequence of a mod that keeps no player data, not a gap to be filled by
 * inventing one.
 *
 * <p>A broadcast reaches every session, so a broadcast payload here carries <b>dimension ids and
 * counts and nothing else</b> — no position (which would map a base), no owner, no port name, no
 * payload contents. The same rule Core's contract imposes on every broadcast topic.
 *
 * <p>The owner UUID is passed <em>into</em> this class by the block entity that holds it and is used
 * only to address the event; it is never stored here, never logged, and never read back out through a
 * getter (see {@code RocketCargoPortBlockEntity.isOwnedBy}). Core's erasure hook scrubs the port, and
 * with it this routing, on request.
 *
 * <p><b>Nothing here may throw at its caller.</b> Every publisher is wrapped: a link failure must never
 * disturb a launch or a delivery.
 *
 * <p>Server thread only.
 */
public final class NeroLogisticsLinkEvents {

    /** Minimum gap between two queue-full alerts for the same port, in milliseconds. */
    private static final long ALERT_COOLDOWN_MS = 300_000L;

    /** Minimum gap between two broadcast queue-full faults, in milliseconds. */
    private static final long FAULT_COOLDOWN_MS = 60_000L;

    /** {@code port position → when its queue-full alert was last raised}. Cleared on server stop. */
    private static final Map<Long, Long> LAST_ALERT = new ConcurrentHashMap<>();

    /** When the queue-full broadcast last went out. Session state; cleared on server stop. */
    private static volatile long lastFault;

    private NeroLogisticsLinkEvents() {
    }

    /**
     * Nothing to subscribe to. NeroLogistics publishes from the two places a shipment's life actually
     * changes — the cargo port's launch attempt and the shipment manager's delivery pass — because
     * those are already the one place each transition is detected. The method exists so
     * {@link NeroLogisticsLinkModule#init()} has the same three-surface shape as every other Nero mod's,
     * and so a future in-mod listener has an obvious home.
     */
    static void init() {
        // Intentionally empty — see the javadoc.
    }

    /** Drops the rate-limiter state. Called from the server-stopped reset hook. */
    public static void reset() {
        LAST_ALERT.clear();
        lastFault = 0L;
    }

    // --- shipment departed ------------------------------------------------------

    /**
     * A cargo port launched. Publishes the owner-scoped detail (only where the port records an owner)
     * plus one broadcast that says only that cargo left one dimension for another.
     *
     * @param owner        the port's recorded owner, or {@code null} — which is the default world's
     *                     answer, and means no owner-scoped event is published at all
     * @param pos          the launching port's position; owner-scoped payload only, never broadcast
     * @param payload      the launched stacks, read for counts only — never their contents
     * @param transitTicks the transit time quoted for this launch (after its QoS lane on the stub route;
     *                     Nerospace's own quote under Nerospace routing), or {@code -1} when no quote was
     *                     available — the ETA fields are then omitted
     */
    public static void shipmentDeparted(@Nullable UUID owner, BlockPos pos, ResourceKey<Level> fromDim,
            ResourceKey<Level> destDim, ShippingClass qos, List<ItemStack> payload, int transitTicks) {
        if (!enabled()) {
            return;
        }
        int stacks = payload == null ? 0 : payload.size();
        long items = 0L;
        if (payload != null) {
            for (ItemStack stack : payload) {
                items += stack.getCount();
            }
        }
        if (owner != null) {
            try {
                JsonObject detail = base();
                detail.addProperty("event", "departed");
                detail.add("position", NeroLogisticsLinkAccess.position(pos));
                detail.addProperty("from_dimension", fromDim.identifier().toString());
                detail.addProperty("dest_dimension", destDim.identifier().toString());
                detail.addProperty("shipping_class", qos.name());
                detail.addProperty("stacks", stacks);
                detail.addProperty("items", items);
                if (transitTicks >= 0) {
                    detail.addProperty("transit_ticks", transitTicks);
                    detail.addProperty("eta_seconds", transitTicks / 20);
                }
                publish(LinkEvent.forPlayer(NeroLogisticsLinkModule.MODULE_ID,
                        NeroLogisticsLinkModule.TOPIC_SHIPMENTS, owner, detail));
            } catch (RuntimeException e) {
                warn(NeroLogisticsLinkModule.TOPIC_SHIPMENTS, e);
            }
        }
        broadcastShipmentState("departed", fromDim, destDim, stacks, items);
    }

    // --- shipment arrived -------------------------------------------------------

    /**
     * Cargo landed. Broadcast only, and necessarily so: a {@code CargoManifest} records dimensions,
     * positions and ticks and <b>never an owner</b>, so at delivery time there is no one to address a
     * private event to. Neither endpoint's position is broadcast.
     *
     * <p>Fired by the built-in (stub) route's delivery pass. Under Nerospace routing the cargo travels as
     * a Nerospace flight and its arrival is Nerospace's own event, so NeroLogistics does not echo it.
     */
    public static void shipmentArrived(CargoManifest manifest) {
        if (!enabled() || manifest == null) {
            return;
        }
        long items = 0L;
        for (ItemStack stack : manifest.items()) {
            items += stack.getCount();
        }
        broadcastShipmentState("arrived", manifest.fromDim(), manifest.destDim(),
                manifest.items().size(), items);
    }

    // --- queue full -------------------------------------------------------------

    /**
     * A port could not launch because the active route's in-transit count is at its hard cap
     * ({@code maxPendingShipments}; stub manifests or tracked Nerospace flights). Raises the
     * owner's alert where the port has one, and broadcasts the fault (rate-limited) either way, because
     * the cap is a server-wide condition rather than one base's problem.
     */
    public static void shipmentQueueFull(MinecraftServer server, @Nullable UUID owner, BlockPos pos) {
        if (!enabled() || server == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFault >= FAULT_COOLDOWN_MS) {
            lastFault = now;
            try {
                JsonObject payload = base();
                payload.addProperty("reason", "shipment_queue_full");
                payload.addProperty("pending", RouteProviders.get().inTransitCount(server));
                payload.addProperty("capacity", NeroLogisticsConfig.maxPendingShipments());
                publish(LinkEvent.broadcast(NeroLogisticsLinkModule.MODULE_ID,
                        NeroLogisticsLinkModule.TOPIC_NETWORK_FAULT, payload));
            } catch (RuntimeException e) {
                warn(NeroLogisticsLinkModule.TOPIC_NETWORK_FAULT, e);
            }
        }
        if (owner == null) {
            return; // nobody to tell — see the class notes
        }
        Long last = LAST_ALERT.get(pos.asLong());
        if (last != null && now - last < ALERT_COOLDOWN_MS) {
            return;
        }
        LAST_ALERT.put(pos.asLong(), now);
        try {
            LinkAlerts.get(server).raise(server, owner,
                    LinkAlert.raise("shipment_queue_full." + pos.asLong(),
                            NeroLogisticsLinkModule.MODULE_ID, LinkAlert.Severity.WARN,
                            "A cargo port has stopped launching: the server's shipment queue is full."));
        } catch (RuntimeException e) {
            warn("alerts", e);
        }
    }

    // --- plumbing ---------------------------------------------------------------

    /** The broadcast twin: two dimension ids, two counts, and which of the two things happened. */
    private static void broadcastShipmentState(String event, ResourceKey<Level> fromDim,
            ResourceKey<Level> destDim, int stacks, long items) {
        try {
            JsonObject payload = base();
            payload.addProperty("event", event);
            payload.addProperty("from_dimension", fromDim.identifier().toString());
            payload.addProperty("dest_dimension", destDim.identifier().toString());
            payload.addProperty("stacks", stacks);
            payload.addProperty("items", items);
            publish(LinkEvent.broadcast(NeroLogisticsLinkModule.MODULE_ID,
                    NeroLogisticsLinkModule.TOPIC_SHIPMENT_STATE, payload));
        } catch (RuntimeException e) {
            warn(NeroLogisticsLinkModule.TOPIC_SHIPMENT_STATE, e);
        }
    }

    private static boolean enabled() {
        return NeroLogisticsLinkAccess.enabled();
    }

    /** The two fields every NeroLogistics event payload starts with. */
    private static JsonObject base() {
        JsonObject payload = new JsonObject();
        payload.addProperty("schema_version", NeroLogisticsLinkModule.SCHEMA_VERSION);
        payload.addProperty("timestamp", System.currentTimeMillis());
        return payload;
    }

    /** Publish to Core's shared bus; a failure there is logged, never thrown at the caller. */
    private static void publish(LinkEvent event) {
        try {
            NeroLinkRegistry.eventBus().publish(event);
        } catch (RuntimeException e) {
            warn(event.topic(), e);
        }
    }

    /** Topic only — never who the event was for (POPIA/GDPR). */
    private static void warn(String topic, RuntimeException e) {
        NeroLogisticsCommon.LOGGER.warn(
                "[NeroLogistics] Publishing the NeroLink '{}' event failed.", topic, e);
    }
}
