package za.co.neroland.nerologistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.registry.ModRegistries;

/**
 * Loader-agnostic entry point for NeroLogistics. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction; on NeoForge/Forge the loader then flushes the DeferredRegisters
 * built here to its mod bus via {@code *RegistrationFactory.registerAll(...)}.
 */
public final class NeroLogisticsCommon {

    public static final String MOD_ID = "nerologistics";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroLogistics");

    private NeroLogisticsCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroLogistics] common init");
        // Register the config schema with Core's manager, then build all content registries.
        NeroLogisticsConfig.load();
        ModRegistries.init();
        // POPIA/GDPR: route opt-in per-player attribution through Core's shared erasure hook, so one
        // erase request (or the inactivity sweep) purges a player here too.
        PlayerDataErasure.register(LogisticsMetrics::erasePlayer);
    }
}
