package za.co.neroland.nerologistics.compat.neroeconomy;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.platform.Services;
import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.ship.ShipmentState;

/**
 * Soft-dependency seam for <b>NeroEconomy</b> — the optional per-launch freight fee. Imports nothing from
 * NeroEconomy: {@link NeroEconomyBridge} is the only class that does, and it is touched only after
 * {@code isModLoaded("neroeconomy")} succeeded. Without NeroEconomy (or with one older than API version 1),
 * or with {@code freightFee=0} (the default), launches stay free.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the dispatcher's UUID is passed to NeroEconomy only for the charge. It is
 * never logged and never placed in a request key — keys are {@code freight/<n>}, a world-wide counter.
 * The {@code delivery_payout} reason is claimed but deliberately unused: paying dispatchers for deliveries
 * would turn freight into a faucet until NeroEconomy has a machine-fulfilment path for delivery jobs.</p>
 */
public final class NeroLogisticsEconomy {

    /** What happened to a freight fee. */
    public enum Fee {
        /** Nothing was due (fee off, NeroEconomy absent) — launch as usual. */
        NONE,
        /** The fee was charged now. */
        PAID,
        /** The dispatcher could not pay — hold the launch. */
        REFUSED
    }

    private static volatile boolean active;
    @Nullable
    private static String lastRefusalKey;

    private NeroLogisticsEconomy() {
    }

    /** Common init: claim the caller handle when NeroEconomy is present. Never throws. */
    public static void init() {
        if (!Services.PLATFORM.isModLoaded("neroeconomy")) {
            return;
        }
        try {
            active = NeroEconomyBridge.init();
        } catch (Throwable t) {
            active = false;
            NeroLogisticsCommon.LOGGER.warn("[NeroLogistics] NeroEconomy integration unavailable ({}); "
                    + "rocket cargo launches stay free", t.getClass().getSimpleName());
            return;
        }
        if (active) {
            NeroLogisticsCommon.LOGGER.info("[NeroLogistics] NeroEconomy detected — optional freight fee "
                    + "available (freightFee, default 0 = off)");
        }
    }

    /** Whether the NeroEconomy bridge is live. */
    public static boolean active() {
        return active;
    }

    /**
     * Charges the configured freight fee to {@code dispatcher}. Server thread only; call after every other
     * launch check on the port has passed.
     */
    public static Fee chargeFreight(MinecraftServer server, UUID dispatcher) {
        int fee = NeroLogisticsConfig.freightFee();
        if (!active || fee <= 0 || server == null || dispatcher == null) {
            return Fee.NONE;
        }
        String key = "freight/" + ShipmentState.get(server).nextFreightSequence();
        String refused = NeroEconomyBridge.charge(server, dispatcher, fee, "freight_fee", key);
        lastRefusalKey = refused;
        return refused == null ? Fee.PAID : Fee.REFUSED;
    }

    /** NeroEconomy's translation key for the most recent refusal (server thread), or {@code null}. */
    @Nullable
    public static String lastRefusalKey() {
        return lastRefusalKey;
    }
}
