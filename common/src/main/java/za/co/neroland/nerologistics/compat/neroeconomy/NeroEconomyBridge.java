package za.co.neroland.nerologistics.compat.neroeconomy;

import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroeconomy.api.EconomyCaller;
import za.co.neroland.neroeconomy.api.EconomyResult;
import za.co.neroland.neroeconomy.api.SiblingEconomy;

/**
 * The only NeroLogistics class that imports NeroEconomy (compiled {@code compileOnly}, never bundled).
 * Loaded only by {@link NeroLogisticsEconomy} after {@code isModLoaded("neroeconomy")}.
 */
final class NeroEconomyBridge {

    private static EconomyCaller caller;

    private NeroEconomyBridge() {
    }

    /** Claims the caller handle; returns false (dormant) when the loaded NeroEconomy is older than API 1. */
    static boolean init() throws ReflectiveOperationException {
        // Read reflectively: the compile-time constant is inlined by javac, so only this sees the loaded jar.
        int api = SiblingEconomy.class.getField("API_VERSION").getInt(null);
        if (api < 1) {
            return false;
        }
        // delivery_payout is reserved (claimed now because claims close at the first server start) and unused.
        caller = SiblingEconomy.claim("nerologistics", Set.of("freight_fee", "delivery_payout"));
        return true;
    }

    /** Player → Reserve. Returns null when charged (or already charged for this key), else the lang key. */
    @Nullable
    static String charge(MinecraftServer server, UUID player, long amount, String reason, String key) {
        if (caller == null) {
            return null;
        }
        EconomyResult result = caller.charge(server, player, amount, reason, key);
        return result.ok() ? null : result.translationKey();
    }
}
