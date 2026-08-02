package za.co.neroland.nerologistics.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerologistics.network.NeroLogisticsNetwork;
import za.co.neroland.nerologistics.platform.INetworkHelper;

/**
 * Fabric side of NeroLogistics' networking seam (ported from Nerotech's {@code FabricNetwork}).
 * {@link #registerCommon()} (mod init, both sides) registers every payload type;
 * {@link #registerClient()} (client init) registers the clientbound receivers, keeping
 * {@code ClientPlayNetworking} off the dedicated server until then. Payload types are
 * NeroLogistics' own — Core drains its list during Core's earlier bootstrap, so NeroLogistics
 * registers here itself. Registered via {@code META-INF/services}.
 */
public final class FabricNetwork implements INetworkHelper {

    /** Mod-init (both sides): payload types + serverbound receivers. */
    public static void registerCommon() {
        for (NeroLogisticsNetwork.Clientbound<?> cb : NeroLogisticsNetwork.clientbound()) {
            registerClientboundType(cb);
        }
        for (NeroLogisticsNetwork.Serverbound<?> sb : NeroLogisticsNetwork.serverbound()) {
            registerServerbound(sb);
        }
    }

    /** Client-init: clientbound receivers (client-only API). */
    public static void registerClient() {
        for (NeroLogisticsNetwork.Clientbound<?> cb : NeroLogisticsNetwork.clientbound()) {
            registerClientReceiver(cb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(
            NeroLogisticsNetwork.Clientbound<T> cb) {
        PayloadTypeRegistry.clientboundPlay().register(cb.type(), cb.codec());
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            NeroLogisticsNetwork.Serverbound<T> sb) {
        PayloadTypeRegistry.serverboundPlay().register(sb.type(), sb.codec());
        ServerPlayNetworking.registerGlobalReceiver(sb.type(), (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> sb.handler().accept(payload, player));
        });
    }

    private static <T extends CustomPacketPayload> void registerClientReceiver(
            NeroLogisticsNetwork.Clientbound<T> cb) {
        ClientPlayNetworking.registerGlobalReceiver(cb.type(), (payload, context) ->
                context.client().execute(() -> cb.handler().accept(payload)));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
