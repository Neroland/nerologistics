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

    /** Register the schema with Core's config manager. Called once from {@code NeroLogisticsCommon.init()}. */
    public static synchronized void load() {
        ConfigManager.register(SCHEMA);
    }
}
