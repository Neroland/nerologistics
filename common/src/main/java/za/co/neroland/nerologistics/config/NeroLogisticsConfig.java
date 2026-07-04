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

    public static boolean telemetryEnabled() {
        return TELEMETRY_ENABLED.get();
    }

    /** Register the schema with Core's config manager. Called once from {@code NeroLogisticsCommon.init()}. */
    public static synchronized void load() {
        ConfigManager.register(SCHEMA);
    }
}
