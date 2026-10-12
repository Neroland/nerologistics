package za.co.neroland.nerologistics.link;

import java.util.List;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;

/**
 * NeroLogistics' plug into Neroland Core's link API — the declaration half. Every section id, action
 * id and event topic the mod exposes is a named constant here, and {@link #init()} is the single
 * registration entry point that {@code NeroLogisticsCommon.init()} calls last.
 *
 * <p>NeroLogistics backs the companion app's <b>drones</b> screen, which until this module existed
 * had no data source at all (the bridge reported {@code nerologistics: absent}), plus the storage,
 * network and shipping surfaces a logistics player actually wants on a phone.
 *
 * <p>The whole module is plain server-side Java against Core's {@link za.co.neroland.nerolandcore.link}
 * package: no networking of its own, no HTTP, no loader wiring beyond one server-handle capture per
 * tick. NeroLogistics declares what it can show and what it can do; the separate NeroLink bridge mod
 * reads Core's registry and serves it. With no bridge installed this costs two registry entries.
 *
 * <h2>Privacy (POPIA/GDPR) — and the ownership problem, honestly</h2>
 *
 * <p><b>NeroLogistics has almost no ownership model, and this module does not invent one.</b> Conduit
 * networks, drive bays, storage cells, drone ports, delivery drones and cargo manifests all record
 * <em>nothing</em> about who built or filled them — they are block- and network-scoped world state by
 * design (see {@code LogisticsMetrics} and {@code CargoManifest}). The single exception is a rocket
 * cargo port, which stores the placing player's UUID <em>only</em> on worlds that opted into
 * {@code perPlayerThroughputAttribution} (default OFF), and which Core's erasure hook scrubs.
 *
 * <p>So rather than hand a companion client a server-wide roster of every base, drive bay and drone
 * port — which would map the whole server — <b>every section is scoped by proximity to the requesting
 * player while they are online</b>, in their own dimension, within
 * {@link NeroLogisticsLinkAccess#SCAN_RADIUS} blocks. That is the same posture Nerospace takes for its
 * unowned rockets and pads: the module answers only what the requester could already see by standing
 * where they are, and nothing at all while they are offline. Each snapshot envelope carries
 * {@code player_online} and {@code scan_radius} so a client can explain an empty list.
 *
 * <p>Consequences worth stating plainly:
 * <ul>
 *   <li>No section ever emits another player's UUID or name — there are none to emit.</li>
 *   <li>A shipment's far side is reported only as the label the game already shows (a dimension name on
 *       the stub route, a pad or station name under Nerospace), never a position.</li>
 *   <li>The Nerospace <b>dispatcher</b> a cargo port records exists to perform the shipping its player
 *       configured; this module never uses it for scoping, events or gating.</li>
 *   <li>The only owner-scoped surface in the whole module is the {@code shipment_queue_full} alert,
 *       which therefore exists only on worlds that opted into attribution.</li>
 *   <li>Broadcast events carry dimensions and counts. Never a position, never an identity.</li>
 * </ul>
 *
 * <p><b>Schema version 1.</b> Bump {@link #SCHEMA_VERSION} whenever the shape of a section changes,
 * so a companion client can tell what it is parsing.
 *
 * @see NeroLogisticsLinkSnapshots
 * @see NeroLogisticsLinkActions
 * @see NeroLogisticsLinkEvents
 */
public final class NeroLogisticsLinkModule {

    /** The link module id — the same string as the mod id, as the ecosystem convention requires. */
    public static final String MODULE_ID = NeroLogisticsCommon.MOD_ID;

    /** The snapshot schema revision. Bump on any change to a section's shape. */
    public static final int SCHEMA_VERSION = 1;

    /** Section: what the storage network holds — drive bays, cell tiers and fill, aggregated totals. */
    public static final String SECTION_STORAGE = "storage";

    /** Section: conduit-network health and throughput per medium, plus controller state. */
    public static final String SECTION_NETWORK = "network";

    /** Section: in-transit cargo with an ETA, plus the cargo ports that launch it. */
    public static final String SECTION_SHIPMENTS = "shipments";

    /** Section: drone ports and the couriers currently in the air around the requester. */
    public static final String SECTION_DRONES = "drones";

    /**
     * Action: set (or cycle) a rocket cargo port's shipping class. Online-required and reach-gated;
     * it moves no items, cancels nothing in flight and is reversible with the same tap.
     */
    public static final String ACTION_SET_SHIPPING_CLASS = "set_shipping_class";

    /** Action: acknowledge one of your own NeroLogistics alerts in Core's per-player alert store. */
    public static final String ACTION_ACKNOWLEDGE_ALERT = "acknowledge_alert";

    /**
     * Topic: a cargo port <em>you own</em> launched a shipment. Owner-scoped, and therefore only ever
     * published on worlds that opted into per-player attribution.
     */
    public static final String TOPIC_SHIPMENTS = "shipments";

    /**
     * Topic: cargo departed or landed somewhere. <b>Broadcast</b> — the payload is a pair of dimension
     * ids, an item/stack count and which of the two things happened. No position, no identity.
     */
    public static final String TOPIC_SHIPMENT_STATE = "shipment_state";

    /**
     * Topic: the server's in-transit shipment queue is at its hard cap, so no port can launch.
     * <b>Broadcast</b>, because the cap is server-wide: the payload is a pending count and the cap.
     */
    public static final String TOPIC_NETWORK_FAULT = "network_fault";

    private NeroLogisticsLinkModule() {
    }

    /**
     * Register the read and write surfaces with Core's link registry, and arm the event publisher.
     * Called <b>last</b> from {@code NeroLogisticsCommon.init()}.
     *
     * <p>Wrapped end to end, including {@link LinkageError}: an older Neroland Core without the link
     * package would otherwise abort mod construction with {@code NoClassDefFoundError} rather than
     * merely losing the companion integration. Ducts, drives, drones and shipping are unaffected
     * either way — the worst outcome is a companion app that reports NeroLogistics as absent, which is
     * also exactly what {@code enableLinkModule=false} produces.
     */
    public static void init() {
        try {
            if (!NeroLogisticsConfig.linkModuleEnabled()) {
                NeroLogisticsCommon.LOGGER.info(
                        "[NeroLogistics] The NeroLink module is disabled by config; companion clients "
                                + "will not see NeroLogistics data.");
                return;
            }
            LinkModuleInfo info = new LinkModuleInfo(MODULE_ID, NeroLogisticsCommon.MOD_VERSION,
                    SCHEMA_VERSION,
                    List.of(SECTION_STORAGE, SECTION_NETWORK, SECTION_SHIPMENTS, SECTION_DRONES),
                    List.of(ACTION_SET_SHIPPING_CLASS, ACTION_ACKNOWLEDGE_ALERT));
            // One provider and one handler cover the whole module; Core keys both on the module id.
            NeroLinkRegistry.registerSnapshotProvider(new NeroLogisticsLinkSnapshots(), info);
            NeroLinkRegistry.registerActionHandler(new NeroLogisticsLinkActions(), info);
            NeroLogisticsLinkEvents.init();
            NeroLogisticsCommon.LOGGER.info("[NeroLogistics] NeroLink module registered (schema v{}).",
                    SCHEMA_VERSION);
        } catch (RuntimeException | LinkageError e) {
            NeroLogisticsCommon.LOGGER.warn(
                    "[NeroLogistics] Could not register the NeroLink module; companion clients will not "
                            + "see NeroLogistics data. Networks, storage and shipping are unaffected.", e);
        }
    }

    /**
     * Capture the running server so snapshots and actions can resolve players, levels and SavedData.
     * Core's SPI hands a provider nothing but a UUID, so the module needs its own handle; each loader's
     * server-tick hook calls this beside the existing {@code ShipmentManager.tick} driver. Cheap (one
     * volatile write) and idempotent.
     */
    public static void rememberServer(MinecraftServer runningServer) {
        NeroLogisticsLinkAccess.rememberServer(runningServer);
    }
}
