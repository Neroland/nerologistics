package za.co.neroland.nerologistics.link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.DronePortBlockEntity;
import za.co.neroland.nerologistics.conduit.NetworkControllerBlockEntity;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.entity.DeliveryDroneEntity;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.DronePortRegistry;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.ship.FlightView;
import za.co.neroland.nerologistics.ship.RouteProvider;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShipDenial;
import za.co.neroland.nerologistics.storage.CellSettings;
import za.co.neroland.nerologistics.storage.DriveBayBlockEntity;
import za.co.neroland.nerologistics.storage.ItemKey;
import za.co.neroland.nerologistics.storage.StorageCellItem;

/**
 * The read half of the link module: what the logistics network around the requester is doing.
 *
 * <h2>Sections</h2>
 *
 * <ul>
 *   <li>{@code storage} — the storage index's aggregated item and fluid totals, plus every drive bay
 *       in reach with its cells' kind, tier, fill, priority and partition state. Accepts
 *       {@code q} (substring match on a registry id or display name) and {@code limit} (1–1000,
 *       default 250) to page a large index onto a phone;</li>
 *   <li>{@code network} — one row per conduit network in reach: medium, size, endpoint count,
 *       controller state and throughput multiplier, plus the dimension's aggregate throughput
 *       counters and the configured per-tick budgets;</li>
 *   <li>{@code shipments} — cargo in transit from around the requester with state and ETA (stub
 *       manifests or Nerospace cargo flights, whichever route is active), plus the rocket cargo ports
 *       in reach with their shipping class, schedule, last refusal, fuel and energy;</li>
 *   <li>{@code drones} — drone ports in reach (mode, name, lanes installed vs in flight, hyperspeed,
 *       buffer, energy) and the delivery drones currently in the air around the requester.</li>
 * </ul>
 *
 * <p>Any other section name yields an empty object, as Core's contract prescribes.
 *
 * <h2>Privacy (POPIA/GDPR)</h2>
 *
 * <p>{@link NeroLogisticsLinkAccess#reach} is the whole visibility rule and it runs first in every
 * section — see {@link NeroLogisticsLinkModule} for why proximity rather than ownership is the honest
 * scope here. No section emits a player identity (the mod stores none outside the opt-in cargo-port
 * owner, which is never read out), and the one place another player's world could be implicated — a
 * shipment's far endpoint — is reported as the label the game already shows and never a position.
 *
 * <p><b>Read-only and bounded.</b> Nothing here mutates anything, no section loads a chunk, and every
 * list is hard-capped ({@code MAX_NETWORKS}, {@code MAX_BLOCKS}, the {@code limit} parameter). The
 * bridge governs how often it is called and caches the result.
 *
 * <p>Server thread only.
 */
public final class NeroLogisticsLinkSnapshots implements LinkSnapshotProvider {

    private static final List<String> SECTIONS = List.of(
            NeroLogisticsLinkModule.SECTION_STORAGE,
            NeroLogisticsLinkModule.SECTION_NETWORK,
            NeroLogisticsLinkModule.SECTION_SHIPMENTS,
            NeroLogisticsLinkModule.SECTION_DRONES);

    /** Default number of aggregated storage lines returned when the client asks for no {@code limit}. */
    private static final int DEFAULT_LINES = 250;

    /** Hard ceiling on aggregated storage lines, whatever {@code limit} asks for. */
    private static final int MAX_LINES = 1_000;

    /** Hard cap on live drone entities listed in one {@code drones} snapshot. */
    private static final int MAX_DRONES = 64;

    /** Hard cap on in-transit shipments listed in one {@code shipments} snapshot. */
    private static final int MAX_SHIPMENTS = 64;

    @Override
    public String moduleId() {
        return NeroLogisticsLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return NeroLogisticsLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        if (playerId == null || section == null) {
            return new JsonObject();
        }
        MinecraftServer server = NeroLogisticsLinkAccess.server();
        if (server == null || !NeroLogisticsLinkAccess.enabled()) {
            return new JsonObject();
        }
        try {
            NeroLogisticsLinkAccess.Reach reach = NeroLogisticsLinkAccess.reach(server, playerId);
            return switch (section) {
                case NeroLogisticsLinkModule.SECTION_STORAGE -> storage(server, playerId, reach, params);
                case NeroLogisticsLinkModule.SECTION_NETWORK -> network(server, playerId, reach);
                case NeroLogisticsLinkModule.SECTION_SHIPMENTS -> shipments(server, playerId, reach);
                case NeroLogisticsLinkModule.SECTION_DRONES -> drones(server, playerId, reach);
                // Unknown section: nothing to say.
                default -> new JsonObject();
            };
        } catch (RuntimeException e) {
            // Section name only — never who asked (POPIA/GDPR). A failed snapshot must not propagate
            // into the bridge.
            NeroLogisticsCommon.LOGGER.warn(
                    "[NeroLogistics] NeroLink snapshot section '{}' failed; returning nothing for it.",
                    section, e);
            return new JsonObject();
        }
    }

    // --- section: storage -----------------------------------------------------

    /**
     * What the storage network in reach is holding. Totals come straight from each network's own
     * {@code NetworkStorageIndex} — the same aggregation a storage terminal shows, computed by the
     * same cached path, so this section costs no extra scan.
     *
     * <p>Drive bays are read from the networks' cached endpoint lists rather than by walking the
     * world, and an unloaded bay simply is not an endpoint. Cell rows carry kind, tier, capacity,
     * stored total, distinct types, priority and whether the cell is partitioned — everything the
     * "which drive is nearly full" question needs, and nothing player-shaped (a cell records nobody).
     */
    private static JsonObject storage(MinecraftServer server, UUID playerId,
            NeroLogisticsLinkAccess.Reach reach, Map<String, String> params) {
        JsonObject root = envelope(server, playerId, reach);
        root.addProperty("storage_network_enabled", NeroLogisticsConfig.enableStorageNetwork());
        JsonArray itemRows = new JsonArray();
        JsonArray fluidRows = new JsonArray();
        JsonArray bayRows = new JsonArray();
        root.add("items", itemRows);
        root.add("fluids", fluidRows);
        root.add("drive_bays", bayRows);
        if (reach == null) {
            return root;
        }

        String query = NeroLogisticsLinkAccess.query(params, "q");
        int limit = NeroLogisticsLinkAccess.boundedQuery(params, "limit", DEFAULT_LINES, 1, MAX_LINES);
        root.addProperty("limit", limit);
        if (query != null) {
            root.addProperty("query", query);
        }

        ServerLevel level = reach.level();
        List<ItemLine> items = new ArrayList<>();
        List<FluidLine> fluids = new ArrayList<>();
        long itemTotal = 0L;
        long fluidTotal = 0L;
        int itemTypes = 0;
        int fluidTypes = 0;
        int bays = 0;
        Set<BlockPos> seenBays = new HashSet<>();

        for (ConduitNetwork network : NeroLogisticsLinkAccess.networksInReach(reach)) {
            if (network.medium() == NetworkMedium.ITEM) {
                for (Map.Entry<ItemKey, Long> entry : network.storageIndex().itemSnapshot(level).entrySet()) {
                    if (entry.getValue() == null || entry.getValue() <= 0) {
                        continue;
                    }
                    itemTypes++;
                    itemTotal += entry.getValue();
                    ItemStack prototype = entry.getKey().prototype();
                    Identifier id = BuiltInRegistries.ITEM.getKey(prototype.getItem());
                    String name = prototype.getHoverName().getString();
                    if (matches(query, id, name)) {
                        items.add(new ItemLine(id, name, entry.getValue()));
                    }
                }
            } else if (network.medium() == NetworkMedium.FLUID) {
                for (Map.Entry<Fluid, Long> entry : network.storageIndex().fluidSnapshot(level).entrySet()) {
                    if (entry.getValue() == null || entry.getValue() <= 0) {
                        continue;
                    }
                    fluidTypes++;
                    fluidTotal += entry.getValue();
                    Identifier id = BuiltInRegistries.FLUID.getKey(entry.getKey());
                    String name = NeroLogisticsLinkAccess.readablePath(id.getPath());
                    if (matches(query, id, name)) {
                        fluids.add(new FluidLine(id, name, entry.getValue()));
                    }
                }
            }
            if (network.medium() == NetworkMedium.ENERGY) {
                continue; // a drive bay is only ever a storage node on an item or fluid network
            }
            // Drive bays: the same endpoint cache the index itself walks, so no extra world scan. A bay
            // touching both an item and a fluid network is one bay, so it is reported once.
            for (BlockPos neighbour : NeroLogisticsLinkAccess.neighboursOf(network, level)) {
                if (bays >= NeroLogisticsLinkAccess.MAX_BLOCKS || !reach.covers(neighbour)
                        || !seenBays.add(neighbour)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(neighbour);
                if (be instanceof DriveBayBlockEntity bay) {
                    bayRows.add(driveBayRow(network, neighbour, bay));
                    bays++;
                }
            }
        }

        items.sort(Comparator.comparingLong(ItemLine::count).reversed());
        for (ItemLine line : items.subList(0, Math.min(limit, items.size()))) {
            JsonObject row = new JsonObject();
            row.addProperty("item", line.id().toString());
            row.addProperty("name", line.name());
            row.addProperty("count", line.count());
            itemRows.add(row);
        }
        fluids.sort(Comparator.comparingLong(FluidLine::amount).reversed());
        for (FluidLine line : fluids.subList(0, Math.min(limit, fluids.size()))) {
            JsonObject row = new JsonObject();
            row.addProperty("fluid", line.id().toString());
            row.addProperty("name", line.name());
            row.addProperty("amount_mb", line.amount());
            fluidRows.add(row);
        }

        root.addProperty("item_types", itemTypes);
        root.addProperty("item_total", itemTotal);
        root.addProperty("fluid_types", fluidTypes);
        root.addProperty("fluid_total_mb", fluidTotal);
        root.addProperty("items_truncated", items.size() > limit);
        root.addProperty("fluids_truncated", fluids.size() > limit);
        return root;
    }

    /** One aggregated item line, before sorting and the {@code limit} cut. */
    private record ItemLine(Identifier id, String name, long count) {
    }

    /** One aggregated fluid line, before sorting and the {@code limit} cut. */
    private record FluidLine(Identifier id, String name, long amount) {
    }

    private static boolean matches(String query, Identifier id, String name) {
        if (query == null) {
            return true;
        }
        return id.toString().toLowerCase(Locale.ROOT).contains(query)
                || name.toLowerCase(Locale.ROOT).contains(query);
    }

    private static JsonObject driveBayRow(ConduitNetwork network, BlockPos pos, DriveBayBlockEntity bay) {
        JsonObject row = new JsonObject();
        row.add("position", NeroLogisticsLinkAccess.position(pos));
        row.addProperty("medium", network.medium().lowerName());
        row.addProperty("bays", DriveBayBlockEntity.BAYS);
        row.addProperty("priority", bay.storagePriority());
        row.addProperty("active", bay.storageNodeActive());
        row.addProperty("fill_fraction", bay.fillFraction());

        JsonArray cells = new JsonArray();
        int installed = 0;
        for (int slot = 0; slot < DriveBayBlockEntity.BAYS; slot++) {
            ItemStack stack = bay.bays().getItem(slot);
            if (!(stack.getItem() instanceof StorageCellItem cell)) {
                continue;
            }
            installed++;
            CellSettings settings = StorageCellItem.settings(stack);
            long capacity = cell.capacity();
            long stored = StorageCellItem.stored(stack);
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", slot);
            entry.addProperty("kind", cell.kind().name());
            entry.addProperty("tier", cell.tier());
            entry.addProperty("capacity", capacity);
            entry.addProperty("stored", stored);
            entry.addProperty("fill_fraction", capacity <= 0 ? 0.0D : (double) stored / capacity);
            entry.addProperty("types", cell.kind() == StorageCellItem.Kind.ITEM
                    ? StorageCellItem.itemContents(stack).typeCount()
                    : StorageCellItem.fluidContents(stack).typeCount());
            entry.addProperty("priority", settings.priority());
            entry.addProperty("partitioned", settings.isPartitioned());
            entry.addProperty("partition_entries", settings.partitionCount());
            cells.add(entry);
        }
        row.addProperty("cells_installed", installed);
        row.add("cells", cells);
        return row;
    }

    // --- section: network -----------------------------------------------------

    /**
     * Conduit-network health per medium. Note there are exactly three media — {@code ITEM},
     * {@code FLUID} and {@code ENERGY}; NeroLogistics has <b>no gas medium</b>, so a client should not
     * expect one and the {@code media} field says so explicitly rather than leaving it to be inferred.
     *
     * <p>Throughput counters are per <em>dimension</em> and are world data, not player data: they count
     * what every network in that dimension moved, keyed by nothing but the dimension id.
     */
    private static JsonObject network(MinecraftServer server, UUID playerId,
            NeroLogisticsLinkAccess.Reach reach) {
        JsonObject root = envelope(server, playerId, reach);
        JsonArray media = new JsonArray();
        for (NetworkMedium medium : NetworkMedium.values()) {
            media.add(medium.lowerName());
        }
        root.add("media", media);

        JsonObject budgets = new JsonObject();
        budgets.addProperty("item_per_tick", NeroLogisticsConfig.itemTransferPerTick());
        budgets.addProperty("fluid_mb_per_tick", NeroLogisticsConfig.fluidTransferPerTick());
        budgets.addProperty("energy_per_tick", NeroLogisticsConfig.energyTransferPerTick());
        budgets.addProperty("max_nodes_per_network", NeroLogisticsConfig.maxNodesPerNetwork());
        root.add("budgets", budgets);

        JsonArray rows = new JsonArray();
        root.add("networks", rows);
        if (reach == null) {
            return root;
        }
        ServerLevel level = reach.level();

        for (ConduitNetwork net : NeroLogisticsLinkAccess.networksInReach(reach)) {
            JsonObject row = new JsonObject();
            row.addProperty("medium", net.medium().lowerName());
            row.addProperty("conduits", net.size());
            row.addProperty("endpoints", net.endpointCount(level));
            row.addProperty("at_node_cap", net.size() >= NeroLogisticsConfig.maxNodesPerNetwork());
            row.addProperty("capacity_multiplier", net.capacityMultiplier(level));
            row.addProperty("controller_conflict", net.controllerConflict());
            BlockPos controller = net.controllerPos();
            row.addProperty("has_controller", controller != null);
            if (controller != null && reach.covers(controller)) {
                row.add("controller_position", NeroLogisticsLinkAccess.position(controller));
                if (level.getBlockEntity(controller) instanceof NetworkControllerBlockEntity be) {
                    row.addProperty("controller_status", be.status().name());
                    row.addProperty("controller_modules", be.moduleCount());
                    row.addProperty("controller_energy", be.energyBuffer().getAmount());
                    row.addProperty("controller_energy_capacity", be.energyBuffer().getCapacity());
                }
            }
            row.addProperty("storage_consumers", net.storageIndex().consumerCount());
            rows.add(row);
        }

        // Aggregate throughput for the dimension the requester is standing in (persisted world data since
        // 0.6.0). Dimension-keyed — it says how much this world moved, never who moved it.
        LogisticsMetrics.Counters counters = LogisticsMetrics.countersOf(server, level.dimension());
        JsonObject throughput = new JsonObject();
        throughput.addProperty("items_moved", counters.itemsMoved);
        throughput.addProperty("fluid_moved_mb", counters.fluidMoved);
        throughput.addProperty("energy_moved", counters.energyMoved);
        throughput.addProperty("drones_dispatched", counters.dronesDispatched);
        throughput.addProperty("shipments_launched", counters.shipmentsLaunched);
        throughput.addProperty("shipments_delivered", counters.shipmentsDelivered);
        throughput.addProperty("shipments_held", counters.shipmentsHeld);
        throughput.addProperty("shipments_dropped", counters.shipmentsDropped);
        root.add("throughput", throughput);
        return root;
    }

    // --- section: shipments ---------------------------------------------------

    /**
     * Cargo in flight from around the requester, and the cargo ports that launch it.
     *
     * <p>Provider-agnostic: both lists come from the same proximity-scoped views the in-game Logistics
     * Dashboard and {@code /nerologistics shipping} use — {@code RouteProvider.flightsNear} for flights
     * (stub manifests, or tracked Nerospace cargo flights under Nerospace routing) and
     * {@code RocketCargoPortBlockEntity.loadedNear} for ports. A flight is listed when its <em>origin</em>
     * port is in reach; its endpoints are reported as the labels the game itself shows (a dimension name
     * on the stub route, a pad or station name under Nerospace) and never as a far-side position.
     *
     * <p><b>Shipping class is a port setting, not a flight field</b>, which is why it appears on the
     * {@code ports} rows and not on the {@code in_transit} rows. The port's dispatcher and opt-in owner
     * are never read out; {@code is_owner} is a boolean about the requester only.
     */
    private static JsonObject shipments(MinecraftServer server, UUID playerId,
            NeroLogisticsLinkAccess.Reach reach) {
        RouteProvider provider = RouteProviders.get();
        JsonObject root = envelope(server, playerId, reach);
        root.addProperty("cross_dimension_enabled", NeroLogisticsConfig.enableCrossDimension());
        root.addProperty("qos_enabled", NeroLogisticsConfig.enableShippingQos());
        root.addProperty("routing", provider.id());
        root.addProperty("pending", provider.inTransitCount(server));
        root.addProperty("pending_capacity", NeroLogisticsConfig.maxPendingShipments());
        root.addProperty("queue_full", provider.atCapacity(server));
        JsonArray transit = new JsonArray();
        JsonArray ports = new JsonArray();
        root.add("in_transit", transit);
        root.add("ports", ports);
        if (reach == null) {
            return root;
        }
        ServerLevel level = reach.level();

        for (FlightView flight : provider.flightsNear(server, level.dimension(), reach.origin(),
                NeroLogisticsLinkAccess.SCAN_RADIUS)) {
            if (transit.size() >= MAX_SHIPMENTS) {
                break;
            }
            long remaining = Math.max(0L, flight.etaTicks());
            JsonObject row = new JsonObject();
            row.addProperty("id", flight.id());
            row.addProperty("origin", flight.origin());
            row.addProperty("destination", flight.destination());
            row.addProperty("state", flight.state().name());
            row.addProperty("eta_ticks", remaining);
            row.addProperty("eta_seconds", flight.etaSeconds());
            if (flight.heldTicks() >= 0) {
                row.addProperty("held_ticks", flight.heldTicks());
            }
            transit.add(row);
        }

        boolean ownFuel = provider.chargesOwnFuel();
        for (RocketCargoPortBlockEntity port : RocketCargoPortBlockEntity.loadedNear(level, reach.origin(),
                NeroLogisticsLinkAccess.SCAN_RADIUS)) {
            if (ports.size() >= NeroLogisticsLinkAccess.MAX_BLOCKS) {
                break;
            }
            JsonObject row = new JsonObject();
            row.add("position", NeroLogisticsLinkAccess.position(port.getBlockPos()));
            row.addProperty("channel", port.channel());
            row.addProperty("shipping_class", port.shippingClass().name());
            row.addProperty("effective_shipping_class", port.effectiveShippingClass().name());
            row.addProperty("schedule", port.schedule().name());
            ShipDenial denial = port.lastDenial();
            if (denial != null) {
                row.addProperty("last_denial", denial.name());
            }
            if (ownFuel) {
                // Only the built-in route burns fuel items from the port; under Nerospace the rocket does.
                row.addProperty("has_fuel", port.hasFuelBuffered(NeroLogisticsConfig.shipFuelPerLaunch()));
            }
            row.addProperty("energy", port.energyBuffer().getAmount());
            row.addProperty("energy_capacity", port.energyBuffer().getCapacity());
            row.addProperty("buffer_slots", port.buffer().getContainerSize());
            row.addProperty("buffer_used", usedSlots(port.buffer()));
            // A boolean about the REQUESTER, which is the only ownership question this mod can answer —
            // and only on worlds that opted into per-player attribution at all.
            row.addProperty("is_owner", port.isOwnedBy(playerId));
            ports.add(row);
        }
        return root;
    }

    // --- section: drones ------------------------------------------------------

    /**
     * The drone fleet around the requester: every drone port in reach and every courier currently in
     * the air near them.
     *
     * <p>A port carries real, distinct state — import/export mode, its routing name, how many drone
     * lanes are installed against how many are in flight, whether a Hyperspeed Card is suppressing the
     * entity entirely, its cargo buffer and its charge — which is exactly the "is my delivery loop
     * healthy?" question a phone is good at. Drone entities are world content the requester can
     * already see out of the window.
     */
    private static JsonObject drones(MinecraftServer server, UUID playerId,
            NeroLogisticsLinkAccess.Reach reach) {
        JsonObject root = envelope(server, playerId, reach);
        root.addProperty("drones_enabled", NeroLogisticsConfig.enableDrones());
        root.addProperty("port_range", NeroLogisticsConfig.dronePortRange());
        root.addProperty("max_drones_per_port", NeroLogisticsConfig.maxDronesPerPort());
        root.addProperty("capacity_per_drone", NeroLogisticsConfig.dronePerDroneCapacity());
        root.addProperty("energy_per_dispatch", NeroLogisticsConfig.dronePortEnergyPerStack());
        JsonArray ports = new JsonArray();
        JsonArray inFlight = new JsonArray();
        root.add("ports", ports);
        root.add("in_flight", inFlight);
        if (reach == null) {
            return root;
        }
        ServerLevel level = reach.level();

        for (BlockPos pos : DronePortRegistry.membersOf(level)) {
            if (ports.size() >= NeroLogisticsLinkAccess.MAX_BLOCKS || !reach.covers(pos)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof DronePortBlockEntity port)) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.add("position", NeroLogisticsLinkAccess.position(pos));
            row.addProperty("name", port.portName());
            row.addProperty("routing", port.portName().isEmpty() ? "auto" : "named");
            row.addProperty("mode", port.mode().name());
            row.addProperty("drones_installed", port.installedDrones());
            row.addProperty("drones_in_flight", port.dronesInFlight());
            row.addProperty("lanes_free",
                    Math.max(0, port.installedDrones() - port.dronesInFlight()));
            row.addProperty("drone_slots", DronePortBlockEntity.DRONE_SLOTS);
            row.addProperty("hyperspeed", port.hasHyperspeed());
            row.addProperty("energy", port.energyBuffer().getAmount());
            row.addProperty("energy_capacity", port.energyBuffer().getCapacity());
            row.addProperty("buffer_slots", port.buffer().getContainerSize());
            row.addProperty("buffer_used", usedSlots(port.buffer()));
            ports.add(row);
        }

        for (DeliveryDroneEntity drone : level.getEntitiesOfClass(DeliveryDroneEntity.class,
                reach.player().getBoundingBox().inflate(NeroLogisticsLinkAccess.SCAN_RADIUS))) {
            if (inFlight.size() >= MAX_DRONES) {
                break;
            }
            JsonObject row = new JsonObject();
            // The entity's own id — world content, not a person.
            row.addProperty("id", drone.getUUID().toString());
            row.add("position", NeroLogisticsLinkAccess.position(drone.blockPosition()));
            row.add("home", NeroLogisticsLinkAccess.position(drone.homePos()));
            inFlight.add(row);
        }
        root.addProperty("in_flight_count", inFlight.size());
        return root;
    }

    // --- helpers --------------------------------------------------------------

    /** How many slots of a block's buffer hold anything. A count, never the contents' owner. */
    private static int usedSlots(net.minecraft.world.Container container) {
        int used = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (!container.getItem(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /**
     * The fields every NeroLogistics snapshot root starts with. {@code player_online} and
     * {@code scan_radius} together let a client explain an empty section without guessing — this
     * module genuinely has nothing to say to an offline player.
     */
    private static JsonObject envelope(MinecraftServer server, UUID playerId,
            NeroLogisticsLinkAccess.Reach reach) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", NeroLogisticsLinkModule.SCHEMA_VERSION);
        root.addProperty("player_online", NeroLogisticsLinkAccess.isOnline(server, playerId));
        root.addProperty("scan_radius", NeroLogisticsLinkAccess.SCAN_RADIUS);
        if (reach != null) {
            root.addProperty("dimension", reach.level().dimension().identifier().toString());
        }
        return root;
    }
}
