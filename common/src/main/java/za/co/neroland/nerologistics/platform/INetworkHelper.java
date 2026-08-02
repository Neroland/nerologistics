package za.co.neroland.nerologistics.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerolandcore.platform.Services;

/**
 * Cross-loader packet-send seam for NeroLogistics' own payloads, resolved per loader through
 * Core's {@link Services} ServiceLoader helper (the same seam {@link IItemLookup} and
 * {@link IFluidLookup} ride) — a direct port of Nerotech's {@code INetworkHelper}.
 *
 * <p>NeroLogistics cannot add payloads to Core's {@code CoreNetwork}: Core's loader modules drain
 * that list during Core's own bootstrap, and Core — as a hard dependency — always initialises
 * before NeroLogistics, so anything added from {@code NeroLogisticsCommon.init()} would arrive
 * after Core's channel is already built. Payload types and handlers are declared once in
 * {@link za.co.neroland.nerologistics.network.NeroLogisticsNetwork}; each loader registers them
 * and implements this send interface. Kept intentionally small — grow as needed.
 */
public interface INetworkHelper {

    INetworkHelper INSTANCE = Services.load(INetworkHelper.class);

    /** Server → one client. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Client → server (call only on the physical client). */
    void sendToServer(CustomPacketPayload payload);
}
