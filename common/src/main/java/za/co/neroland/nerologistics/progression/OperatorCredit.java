package za.co.neroland.nerologistics.progression;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.nerolandcore.progression.ProgressionGates;
import za.co.neroland.nerologistics.NeroLogisticsCommon;

/**
 * Credits per-player record gates to the player acting on a block <em>right now</em>. NeroLogistics
 * records no owner for networks, drone hubs or drone ports, so the player who places or configures
 * one is noted in memory only (never saved, never logged, dropped when the block entity unloads or on
 * erasure) and credited when the block does the thing the gate marks. The gate row itself lives in
 * Core's progression store, which Core's erasure covers. Server thread only.
 */
public final class OperatorCredit {

    /** First drone a player's hub or port sends out. */
    public static final Identifier FIRST_DRONE_DISPATCH =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "first_drone_dispatch");
    /** A network controller the player placed or configured takes charge of a network. */
    public static final Identifier STORAGE_NETWORK_ONLINE =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "storage_network_online");

    private static final OperatorLedger<BlockEntity> LEDGER = new OperatorLedger<>();

    private OperatorCredit() {
    }

    /** {@code player} just placed or configured {@code be}. */
    public static void note(BlockEntity be, @Nullable UUID player) {
        LEDGER.note(be, player);
    }

    /** Open {@code gate} for whoever was noted on {@code be}, once; a no-op when nobody was. */
    public static void credit(@Nullable MinecraftServer server, BlockEntity be, Identifier gate) {
        if (server == null) {
            return;
        }
        UUID player = LEDGER.take(be);
        if (player != null) {
            ProgressionGates.open(server, player, gate);
        }
    }

    /** POPIA/GDPR erasure. */
    public static void erasePlayer(UUID player) {
        LEDGER.forget(player);
    }

    /** Server stop / world switch. */
    public static void clearAll() {
        LEDGER.clear();
    }
}
