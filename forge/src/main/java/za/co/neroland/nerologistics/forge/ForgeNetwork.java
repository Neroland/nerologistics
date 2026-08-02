package za.co.neroland.nerologistics.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.network.NeroLogisticsNetwork;
import za.co.neroland.nerologistics.platform.INetworkHelper;

/**
 * Forge side of NeroLogistics' networking seam (ported from Nerotech's {@code ForgeNetwork}):
 * builds NeroLogistics' own {@code nerologistics:main} channel from the
 * {@link NeroLogisticsNetwork} payload list (Core's channel is sealed during Core's earlier
 * bootstrap, so NeroLogistics cannot piggyback on it) and implements the send seam. Registered via
 * {@code META-INF/services}.
 */
public final class ForgeNetwork implements INetworkHelper {

    private static Channel<CustomPacketPayload> channel;

    public static void register() {
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play =
                ChannelBuilder.named(Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "main"))
                        .optional()
                        .payloadChannel()
                        .play()
                        .bidirectional();
        for (NeroLogisticsNetwork.Clientbound<?> cb : NeroLogisticsNetwork.clientbound()) {
            registerClientbound(play, cb);
        }
        for (NeroLogisticsNetwork.Serverbound<?> sb : NeroLogisticsNetwork.serverbound()) {
            registerServerbound(play, sb);
        }
        channel = play.build();
    }

    private static <T extends CustomPacketPayload> void registerClientbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            NeroLogisticsNetwork.Clientbound<T> cb) {
        // The flow is bidirectional, so ignore this clientbound payload if a client sends it upstream.
        play.addMain(cb.type(), registryCodec(cb.codec()), (payload, context) -> {
            if (context.isClientSide()) {
                cb.handler().accept(payload);
            }
        });
    }

    private static <T extends CustomPacketPayload> void registerServerbound(
            PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> play,
            NeroLogisticsNetwork.Serverbound<T> sb) {
        // The flow is bidirectional; only handle this serverbound payload when a real player sent it.
        play.addMain(sb.type(), registryCodec(sb.codec()), (payload, context) -> {
            if (context.getSender() instanceof ServerPlayer serverPlayer) {
                sb.handler().accept(payload, serverPlayer);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> registryCodec(
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return (StreamCodec<RegistryFriendlyByteBuf, T>) codec;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.PLAYER.with(player));
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (channel != null) {
            channel.send(payload, PacketDistributor.SERVER.noArg());
        }
    }
}
