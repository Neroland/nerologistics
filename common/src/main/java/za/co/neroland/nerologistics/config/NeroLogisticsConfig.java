package za.co.neroland.nerologistics.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

/**
 * NeroLogistics config (managed by Neroland Core's {@link ConfigManager}). Stage-2 local-transport
 * balance: per-network-per-tick throughput budgets for each medium, and the node cap that bounds how
 * large a single network may grow (oversized builds refuse to connect rather than costing tick time).
 * All values are server-authoritative — the server's values win and sync to clients.
 */
public final class NeroLogisticsConfig {

    private static final ConfigSchema SCHEMA = ConfigSchema.create("nerologistics",
            "NeroLogistics config (managed by Neroland Core). Local-transport (duct/cable) balance.");

    private static final ConfigValue<Integer> ITEM_PER_TICK = SCHEMA.intRange("itemTransferPerTick",
            64, 1, 65_536, true, "max items moved per item network per tick");

    private static final ConfigValue<Integer> FLUID_PER_TICK = SCHEMA.intRange("fluidTransferPerTick",
            1_000, 1, 16_000_000, true, "max mB moved per fluid network per tick");

    private static final ConfigValue<Integer> ENERGY_PER_TICK = SCHEMA.intRange("energyTransferPerTick",
            2_560, 1, 100_000_000, true, "max NE moved per energy network per tick");

    private static final ConfigValue<Integer> MAX_NODES = SCHEMA.intRange("maxNodesPerNetwork",
            2_000, 1, 1_000_000, true,
            "max conduits in one network; a conduit that would exceed it stays isolated (no lag spiral)");

    // --- Stage 7: network controller + modular capacity --------------------
    private static final ConfigValue<Boolean> ENABLE_CONTROLLER = SCHEMA.bool("enableController",
            true, true, "master toggle for the network controller's managed throughput boost");

    private static final ConfigValue<Integer> CONTROLLER_UPKEEP = SCHEMA.intRange("controllerUpkeepPerTick",
            8, 0, 1_000_000, true,
            "NE drawn per tick to keep a controller powered; below this the controller manages at base speed");

    private static final ConfigValue<Integer> CONTROLLER_MODULE_BONUS = SCHEMA.intRange("controllerModuleBonusPercent",
            25, 0, 1_000, true, "throughput bonus (%) each connected network module adds to a powered controller");

    private static final ConfigValue<Integer> CONTROLLER_MAX_MODULES = SCHEMA.intRange("controllerMaxModules",
            16, 0, 4_096, true, "max network modules a single controller counts (bounds the flood-fill)");

    private static final ConfigValue<Integer> CONTROLLER_MAX_PERCENT = SCHEMA.intRange("controllerMaxPercent",
            500, 100, 100_000, true, "cap on a controller's managed throughput multiplier (100 = no boost)");

    // --- Stage 12: native trains -------------------------------------------
    private static final ConfigValue<Boolean> ENABLE_TRAINS = SCHEMA.bool("enableTrains",
            true, true, "master toggle for native train stations");

    private static final ConfigValue<Integer> TRAIN_INTERVAL = SCHEMA.intRange("trainStationIntervalTicks",
            100, 1, 72_000, true, "ticks between a load station's bulk haul attempts");

    private static final ConfigValue<Integer> TRAIN_TICKS_PER_BLOCK = SCHEMA.intRange("trainTicksPerBlock",
            2, 0, 1_200, true, "transit ticks added per block of distance between stations (slower = more travel feel)");

    private static final ConfigValue<Integer> TRAIN_MIN_TRANSIT = SCHEMA.intRange("trainMinTransitTicks",
            40, 1, 1_728_000, true, "minimum transit time for a train haul");

    private static final ConfigValue<Integer> TRAIN_MAX_RANGE = SCHEMA.intRange("trainMaxRange",
            2_048, 1, 30_000_000, true, "max block distance a load station ships to an unload station");

    private static final ConfigValue<Integer> TRAIN_BULK_PER_TRIP = SCHEMA.intRange("trainBulkPerTrip",
            27, 1, 54, true, "max buffer slots a station hauls per trip (bulk)");

    // --- Stage 11: drone ports ---------------------------------------------
    private static final ConfigValue<Integer> MAX_DRONES_PER_PORT = SCHEMA.intRange("maxDronesPerPort",
            8, 1, 256, true, "hard cap on drones (parallel lanes) a single drone port counts");

    private static final ConfigValue<Integer> DRONE_PORT_RANGE = SCHEMA.intRange("dronePortRange",
            256, 1, 4_096, true, "max block distance an export port will ship to an import port");

    private static final ConfigValue<Integer> DRONE_PER_DRONE_CAPACITY = SCHEMA.intRange("dronePerDroneCapacity",
            16, 1, 64, true, "items each drone carries per dispatch (throughput = drones × this)");

    private static final ConfigValue<Integer> DRONE_PORT_ENERGY_PER_STACK = SCHEMA.intRange("dronePortEnergyPerStack",
            256, 0, 10_000_000, true, "NE charged per drone dispatch from a port");

    // --- Stage 10: buffer blocks -------------------------------------------
    private static final ConfigValue<Boolean> ENABLE_BUFFERS = SCHEMA.bool("enableBuffers",
            true, true, "master toggle for keep-stocked buffer leveling (passive buffers always hold)");

    private static final ConfigValue<Integer> BUFFER_INTERVAL = SCHEMA.intRange("bufferIntervalTicks",
            20, 1, 1_200, true, "ticks between a keep-stocked buffer's top-up/overflow passes");

    // --- Stage 9: native auto-crafting -------------------------------------
    private static final ConfigValue<Boolean> ENABLE_AUTO_CRAFTING = SCHEMA.bool("enableAutoCrafting",
            true, true, "master toggle for the auto-crafter");

    private static final ConfigValue<Integer> CRAFT_INTERVAL = SCHEMA.intRange("craftIntervalTicks",
            20, 1, 1_200, true, "ticks between an auto-crafter's crafting passes");

    private static final ConfigValue<Integer> AUTO_CRAFT_ENERGY = SCHEMA.intRange("autoCraftEnergyPerCraft",
            200, 0, 10_000_000, true, "NE charged per item crafted by an auto-crafter");

    private static final ConfigValue<Integer> AUTO_CRAFTS_PER_INTERVAL = SCHEMA.intRange("autoCraftsPerInterval",
            4, 1, 4_096, true,
            "base crafts an auto-crafter runs per interval (scaled by the managing controller's capacity)");

    // --- Stage 3: wireless + drones ----------------------------------------
    private static final ConfigValue<Integer> WIRELESS_RANGE = SCHEMA.intRange("wirelessRange",
            64, 1, 1_024, true, "max block distance between two wireless terminals on the same channel");

    private static final ConfigValue<Integer> WIRELESS_ENERGY_PER_ITEM = SCHEMA.intRange("wirelessEnergyPerItem",
            16, 0, 1_000_000, true, "NE charged per item moved over a wireless channel");

    private static final ConfigValue<Integer> WIRELESS_INTERVAL = SCHEMA.intRange("wirelessIntervalTicks",
            10, 1, 1_200, true, "ticks between wireless-channel transport passes (query batching window)");

    private static final ConfigValue<Integer> DRONE_RANGE = SCHEMA.intRange("droneRange",
            48, 1, 512, true, "max block distance a delivery drone will travel from its hub");

    private static final ConfigValue<Integer> DRONES_PER_HUB = SCHEMA.intRange("dronesPerHub",
            4, 1, 64, true, "hard cap on live drone entities a single hub may have in flight");

    private static final ConfigValue<Integer> DRONE_ENERGY_PER_DELIVERY = SCHEMA.intRange("droneEnergyPerDelivery",
            512, 0, 10_000_000, true, "NE charged per drone delivery dispatched");

    // --- Stage 4: cross-dimension shipping ---------------------------------
    private static final ConfigValue<Integer> SHIP_TRANSIT_TICKS = SCHEMA.intRange("shipTransitTicks",
            1_200, 20, 1_728_000, true, "transit time (ticks) for a cross-dimension cargo shipment");

    private static final ConfigValue<Integer> SHIP_ENERGY_PER_STACK = SCHEMA.intRange("shipEnergyPerStack",
            10_000, 0, 100_000_000, true, "NE charged per stack in a shipment (deliberately expensive)");

    private static final ConfigValue<Integer> SHIP_FUEL_PER_LAUNCH = SCHEMA.intRange("shipFuelPerLaunch",
            1, 0, 64, true, "rocket-fuel-tagged items consumed per launch");

    private static final ConfigValue<Integer> SHIP_INTERVAL_TICKS = SCHEMA.intRange("shipIntervalTicks",
            100, 1, 72_000, true, "ticks between a cargo port's auto-ship attempts");

    // --- Stage 5: dashboards + POPIA/GDPR ----------------------------------
    // Default OFF: with attribution off, NeroLogistics stores NO personal data at all (everything is
    // block/network-keyed). Turning it on records cargo-port shipments against the placing player's
    // UUID only (never a name), retention-pruned and erasable via Core's data-erasure hook.
    private static final ConfigValue<Boolean> PER_PLAYER_ATTRIBUTION = SCHEMA.bool("perPlayerThroughputAttribution",
            false, true,
            "opt-in: attribute cargo-port shipments to the placing player (UUID only); off = no player data");

    private static final ConfigValue<Integer> ATTRIBUTION_RETENTION_DAYS = SCHEMA.intRange("attributionRetentionDays",
            30, 0, 3_650, true,
            "days to retain per-player attribution before auto-prune (0 = keep until erased)");

    // --- Stage 6: per-feature toggles + hardening caps ---------------------
    private static final ConfigValue<Boolean> ENABLE_WIRELESS = SCHEMA.bool("enableWireless",
            true, true, "master toggle for wireless cargo terminals");

    private static final ConfigValue<Boolean> ENABLE_DRONES = SCHEMA.bool("enableDrones",
            true, true, "master toggle for drone hubs + delivery drones");

    private static final ConfigValue<Boolean> ENABLE_CROSS_DIMENSION = SCHEMA.bool("enableCrossDimension",
            true, true, "master toggle for rocket cargo ports / cross-dimension shipping");

    private static final ConfigValue<Integer> MAX_PENDING_SHIPMENTS = SCHEMA.intRange("maxPendingShipments",
            1_024, 1, 1_000_000, true,
            "hard cap on in-transit shipments; ports stop launching at the cap (no unbounded queue)");

    // --- Stage 14: digital storage network ---------------------------------
    // Drive bays hold a fixed 6 cells each (deliberately not configurable — GUI + comparator
    // assume it); cell capacities are pure counts (no AE2 byte/type math).
    private static final ConfigValue<Boolean> ENABLE_STORAGE_NETWORK = SCHEMA.bool("enableStorageNetwork",
            true, true, "master toggle for the digital storage network (storage cells, drive bays, "
            + "network index); off = drive bays inert, index empty");

    private static final ConfigValue<Integer> ITEM_CELL_CAP_1K = SCHEMA.intRange("itemCellCapacity1k",
            1_000, 1, 100_000_000, true, "total items a tier-1 (1k) item cell holds");

    private static final ConfigValue<Integer> ITEM_CELL_CAP_8K = SCHEMA.intRange("itemCellCapacity8k",
            8_000, 1, 100_000_000, true, "total items a tier-2 (8k) item cell holds");

    private static final ConfigValue<Integer> ITEM_CELL_CAP_64K = SCHEMA.intRange("itemCellCapacity64k",
            64_000, 1, 100_000_000, true, "total items a tier-3 (64k) item cell holds");

    private static final ConfigValue<Integer> ITEM_CELL_CAP_512K = SCHEMA.intRange("itemCellCapacity512k",
            512_000, 1, 100_000_000, true, "total items a tier-4 (512k) item cell holds");

    private static final ConfigValue<Integer> FLUID_CELL_CAP_16B = SCHEMA.intRange("fluidCellCapacity16b",
            16, 1, 1_000_000, true, "total buckets a tier-1 (16B) fluid cell holds");

    private static final ConfigValue<Integer> FLUID_CELL_CAP_128B = SCHEMA.intRange("fluidCellCapacity128b",
            128, 1, 1_000_000, true, "total buckets a tier-2 (128B) fluid cell holds");

    private static final ConfigValue<Integer> FLUID_CELL_CAP_1024B = SCHEMA.intRange("fluidCellCapacity1024b",
            1_024, 1, 1_000_000, true, "total buckets a tier-3 (1024B) fluid cell holds");

    private static final ConfigValue<Integer> FLUID_CELL_CAP_8192B = SCHEMA.intRange("fluidCellCapacity8192b",
            8_192, 1, 1_000_000, true, "total buckets a tier-4 (8192B) fluid cell holds");

    private static final ConfigValue<Integer> INDEX_REFRESH = SCHEMA.intRange("storageIndexRefreshTicks",
            20, 1, 1_200, true,
            "min ticks between the network storage index's read-through container rescans");

    // --- Stage 15: storage terminal + wireless portable terminal -----------
    private static final ConfigValue<Boolean> ENABLE_STORAGE_TERMINAL = SCHEMA.bool("enableStorageTerminal",
            true, true, "master toggle for the storage terminal + wireless terminal GUIs; "
            + "off = the blocks/items stay but refuse to open (clean degrade)");

    private static final ConfigValue<Integer> TERMINAL_RESYNC = SCHEMA.intRange("terminalResyncTicks",
            10, 1, 1_200, true,
            "min ticks between storage-terminal content re-syncs to an open viewer (only sent on change)");

    private static final ConfigValue<Integer> WIRELESS_TERMINAL_RANGE = SCHEMA.intRange("wirelessTerminalRange",
            64, -1, 30_000_000, true,
            "max block distance from the bound network controller a wireless terminal works at "
            + "(same dimension only; -1 = unlimited)");

    // --- Stage 16: logistics processor (rule-based supply policies) ---------
    private static final ConfigValue<Boolean> ENABLE_LOGISTICS_PROCESSOR = SCHEMA.bool("enableLogisticsProcessor",
            true, true, "master toggle for the logistics processor; off = rules stop evaluating "
            + "(the block stays placeable but idles)");

    private static final ConfigValue<Integer> LOGISTICS_RULE_INTERVAL = SCHEMA.intRange("logisticsRuleIntervalTicks",
            40, 1, 72_000, true, "ticks between a logistics processor's rule-evaluation passes (never per-tick)");

    private static final ConfigValue<Integer> LOGISTICS_ACTION_CAP = SCHEMA.intRange("logisticsActionCapPerCycle",
            64, 1, 65_536, true, "max items a single rule moves per evaluation cycle");

    private static final ConfigValue<Integer> LOGISTICS_ENERGY_PER_ACTION = SCHEMA.intRange("logisticsEnergyPerAction",
            100, 0, 10_000_000, true, "NE charged per executed rule action (0 = free)");

    // --- Stage 17: shipping QoS lanes (express vs bulk) ---------------------
    private static final ConfigValue<Boolean> ENABLE_SHIPPING_QOS = SCHEMA.bool("enableShippingQos",
            true, true, "master toggle for rocket-cargo-port shipping classes; "
            + "off = every port ships STANDARD regardless of its configured class");

    private static final ConfigValue<Integer> EXPRESS_TRANSIT_FACTOR = SCHEMA.intRange("expressTransitFactor",
            25, 1, 100, true, "EXPRESS transit time as % of the route's base (25 = four times faster; min 20 ticks)");

    private static final ConfigValue<Integer> EXPRESS_FUEL_FACTOR = SCHEMA.intRange("expressFuelFactor",
            300, 100, 10_000, true, "EXPRESS fuel cost as % of the route's base (300 = triple fuel)");

    private static final ConfigValue<Integer> BULK_TRANSIT_FACTOR = SCHEMA.intRange("bulkTransitFactor",
            200, 100, 10_000, true, "BULK transit time as % of the route's base (200 = twice as slow)");

    private static final ConfigValue<Integer> BULK_FUEL_FACTOR = SCHEMA.intRange("bulkFuelFactor",
            50, 1, 100, true, "BULK fuel cost as % of the route's base (50 = half fuel, rounded up, min 1)");

    // --- Telemetry (anonymous crash reporting; CLIENT-LOCAL opt-out, not server-synced) ----
    private static final ConfigValue<Boolean> TELEMETRY_ENABLED = SCHEMA.bool("telemetryEnabled",
            true, false, "anonymous error reporting to the developers (stack trace + mod/MC/loader/OS/Java "
            + "versions only — never names, UUIDs, IPs, or world data; POPIA/GDPR-compliant). Set false to "
            + "opt out");

    private NeroLogisticsConfig() {
    }

    public static int itemTransferPerTick() {
        return ITEM_PER_TICK.get();
    }

    public static int fluidTransferPerTick() {
        return FLUID_PER_TICK.get();
    }

    public static int energyTransferPerTick() {
        return ENERGY_PER_TICK.get();
    }

    public static int maxNodesPerNetwork() {
        return MAX_NODES.get();
    }

    public static boolean enableTrains() {
        return ENABLE_TRAINS.get();
    }

    public static int trainStationIntervalTicks() {
        return TRAIN_INTERVAL.get();
    }

    public static int trainTicksPerBlock() {
        return TRAIN_TICKS_PER_BLOCK.get();
    }

    public static int trainMinTransitTicks() {
        return TRAIN_MIN_TRANSIT.get();
    }

    public static int trainMaxRange() {
        return TRAIN_MAX_RANGE.get();
    }

    public static int trainBulkPerTrip() {
        return TRAIN_BULK_PER_TRIP.get();
    }

    public static int maxDronesPerPort() {
        return MAX_DRONES_PER_PORT.get();
    }

    public static int dronePortRange() {
        return DRONE_PORT_RANGE.get();
    }

    public static int dronePerDroneCapacity() {
        return DRONE_PER_DRONE_CAPACITY.get();
    }

    public static int dronePortEnergyPerStack() {
        return DRONE_PORT_ENERGY_PER_STACK.get();
    }

    public static boolean enableBuffers() {
        return ENABLE_BUFFERS.get();
    }

    public static int bufferIntervalTicks() {
        return BUFFER_INTERVAL.get();
    }

    public static boolean enableAutoCrafting() {
        return ENABLE_AUTO_CRAFTING.get();
    }

    public static int craftIntervalTicks() {
        return CRAFT_INTERVAL.get();
    }

    public static int autoCraftEnergyPerCraft() {
        return AUTO_CRAFT_ENERGY.get();
    }

    public static int autoCraftsPerInterval() {
        return AUTO_CRAFTS_PER_INTERVAL.get();
    }

    public static boolean enableController() {
        return ENABLE_CONTROLLER.get();
    }

    public static int controllerUpkeepPerTick() {
        return CONTROLLER_UPKEEP.get();
    }

    public static int controllerModuleBonusPercent() {
        return CONTROLLER_MODULE_BONUS.get();
    }

    public static int controllerMaxModules() {
        return CONTROLLER_MAX_MODULES.get();
    }

    public static int controllerMaxPercent() {
        return CONTROLLER_MAX_PERCENT.get();
    }

    public static int wirelessRange() {
        return WIRELESS_RANGE.get();
    }

    public static int wirelessEnergyPerItem() {
        return WIRELESS_ENERGY_PER_ITEM.get();
    }

    public static int wirelessIntervalTicks() {
        return WIRELESS_INTERVAL.get();
    }

    public static int droneRange() {
        return DRONE_RANGE.get();
    }

    public static int dronesPerHub() {
        return DRONES_PER_HUB.get();
    }

    public static int droneEnergyPerDelivery() {
        return DRONE_ENERGY_PER_DELIVERY.get();
    }

    public static int shipTransitTicks() {
        return SHIP_TRANSIT_TICKS.get();
    }

    public static int shipEnergyPerStack() {
        return SHIP_ENERGY_PER_STACK.get();
    }

    public static int shipFuelPerLaunch() {
        return SHIP_FUEL_PER_LAUNCH.get();
    }

    public static int shipIntervalTicks() {
        return SHIP_INTERVAL_TICKS.get();
    }

    public static boolean perPlayerThroughputAttribution() {
        return PER_PLAYER_ATTRIBUTION.get();
    }

    public static int attributionRetentionDays() {
        return ATTRIBUTION_RETENTION_DAYS.get();
    }

    public static boolean enableWireless() {
        return ENABLE_WIRELESS.get();
    }

    public static boolean enableDrones() {
        return ENABLE_DRONES.get();
    }

    public static boolean enableCrossDimension() {
        return ENABLE_CROSS_DIMENSION.get();
    }

    public static int maxPendingShipments() {
        return MAX_PENDING_SHIPMENTS.get();
    }

    public static boolean enableStorageNetwork() {
        return ENABLE_STORAGE_NETWORK.get();
    }

    /** Item-cell capacity (total items) for tier {@code 0..3}; out-of-range tiers clamp. */
    public static int itemCellCapacity(int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 0 -> ITEM_CELL_CAP_1K.get();
            case 1 -> ITEM_CELL_CAP_8K.get();
            case 2 -> ITEM_CELL_CAP_64K.get();
            default -> ITEM_CELL_CAP_512K.get();
        };
    }

    /** Fluid-cell capacity (total buckets) for tier {@code 0..3}; out-of-range tiers clamp. */
    public static int fluidCellCapacityBuckets(int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 0 -> FLUID_CELL_CAP_16B.get();
            case 1 -> FLUID_CELL_CAP_128B.get();
            case 2 -> FLUID_CELL_CAP_1024B.get();
            default -> FLUID_CELL_CAP_8192B.get();
        };
    }

    public static int storageIndexRefreshTicks() {
        return INDEX_REFRESH.get();
    }

    public static boolean enableStorageTerminal() {
        return ENABLE_STORAGE_TERMINAL.get();
    }

    public static int terminalResyncTicks() {
        return TERMINAL_RESYNC.get();
    }

    /** Wireless-terminal working radius from its bound controller; {@code -1} = unlimited. */
    public static int wirelessTerminalRange() {
        return WIRELESS_TERMINAL_RANGE.get();
    }

    public static boolean enableLogisticsProcessor() {
        return ENABLE_LOGISTICS_PROCESSOR.get();
    }

    public static int logisticsRuleIntervalTicks() {
        return LOGISTICS_RULE_INTERVAL.get();
    }

    public static int logisticsActionCapPerCycle() {
        return LOGISTICS_ACTION_CAP.get();
    }

    public static int logisticsEnergyPerAction() {
        return LOGISTICS_ENERGY_PER_ACTION.get();
    }

    public static boolean enableShippingQos() {
        return ENABLE_SHIPPING_QOS.get();
    }

    public static int expressTransitFactor() {
        return EXPRESS_TRANSIT_FACTOR.get();
    }

    public static int expressFuelFactor() {
        return EXPRESS_FUEL_FACTOR.get();
    }

    public static int bulkTransitFactor() {
        return BULK_TRANSIT_FACTOR.get();
    }

    public static int bulkFuelFactor() {
        return BULK_FUEL_FACTOR.get();
    }

    public static boolean telemetryEnabled() {
        return TELEMETRY_ENABLED.get();
    }

    /** Register the schema with Core's config manager. Called once from {@code NeroLogisticsCommon.init()}. */
    public static synchronized void load() {
        ConfigManager.register(SCHEMA);
    }
}
