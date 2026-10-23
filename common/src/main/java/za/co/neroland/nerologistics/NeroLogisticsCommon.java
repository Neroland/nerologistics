package za.co.neroland.nerologistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.nerologistics.compat.NerospaceCompat;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.link.NeroLogisticsLinkModule;
import za.co.neroland.nerologistics.network.NeroLogisticsNetwork;
import za.co.neroland.nerologistics.quest.QuestHooks;
import za.co.neroland.nerologistics.registry.ModRegistries;
import za.co.neroland.nerologistics.world.QuestPerksState;

/**
 * Loader-agnostic entry point for NeroLogistics. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction; on NeoForge/Forge the loader then flushes the DeferredRegisters
 * built here to its mod bus via {@code *RegistrationFactory.registerAll(...)}.
 */
public final class NeroLogisticsCommon {

    public static final String MOD_ID = "nerologistics";

    /**
     * Mirrors {@code mod_version} in gradle.properties — the public version string surfaces that have
     * no loader metadata handy report (currently the NeroLink module's discovery entry). <b>Release
     * checklist: every {@code mod_version} bump must update this constant too</b>; it is not
     * build-injected.
     */
    public static final String MOD_VERSION = "0.7.0-beta.1";

    public static final Logger LOGGER = LoggerFactory.getLogger("NeroLogistics");

    private NeroLogisticsCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroLogistics] common init");
        // Register the config schema with Core's manager, then build all content registries.
        NeroLogisticsConfig.load();
        ModRegistries.init();
        // Declare NeroLogistics' own payloads before each loader wires them to its channel
        // (Core's channel is sealed during Core's earlier bootstrap — see NeroLogisticsNetwork).
        NeroLogisticsNetwork.init();
        // Nerospace soft-dep: when present, swap the stub route provider for the nerospace.api
        // planet/station catalog (reflective binding — never a compile-time Nerospace import).
        NerospaceCompat.init();
        // NeroEconomy soft-dep: claims the caller handle for the optional freight fee (common init only).
        // Inert without NeroEconomy, and freightFee defaults to 0 = off.
        za.co.neroland.nerologistics.compat.neroeconomy.NeroLogisticsEconomy.init();
        // Quest-pack hooks: turn Core's hook/* record-gate opens into per-player cosmetic perks. Consumes Core
        // progression events only — no quest mod is imported or required.
        QuestHooks.init();
        // POPIA/GDPR: route opt-in per-player attribution through Core's shared erasure hook, so one
        // erase request (or the inactivity sweep) purges a player here too — both the in-memory
        // attribution map and any rocket-cargo-port owner UUIDs (loaded ports immediately; unloaded
        // ports via the durable tombstone list they consult on next load), plus any quest-unlocked perks.
        PlayerDataErasure.register((server, player) -> {
            LogisticsMetrics.erasePlayer(server, player);
            RocketCargoPortBlockEntity.erasePlayer(server, player);
            QuestPerksState perks = QuestPerksState.get(server);
            if (perks.erase(player)) {
                za.co.neroland.nerolandcore.data.SavedDataRecovery.backupNow(server.overworld(),
                        QuestPerksState.TYPE, perks, QuestPerksState.ID.toString());
            }
        });
        // LAST: expose NeroLogistics to companion clients through Core's link registry. Registered
        // after everything above so a client is never told about a subsystem the mod has not finished
        // wiring, and wrapped internally so a link failure can never take the mod down.
        NeroLogisticsLinkModule.init();
    }
}
