package za.co.neroland.nerologistics.neoforge;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import za.co.neroland.nerologistics.network.NeroLogisticsNetwork;
import za.co.neroland.nerologistics.platform.INetworkHelper;

/**
 * NeoForge side of NeroLogistics' networking seam (ported from Nerotech's {@code NeoForgeNetwork}):
 * registers every {@link NeroLogisticsNetwork} payload during {@code RegisterPayloadHandlersEvent}
 * (on NeroLogistics' mod event bus) and implements the send seam. Registered via
 * {@code META-INF/services}.
 */
public final class NeoForgeNetwork implements INetworkHelper {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        for (NeroLogisticsNetwork.Clientbound<?> cb : NeroLogisticsNetwork.clientbound()) {
            registerClientbound(registrar, cb);
        }
        for (NeroLogisticsNetwork.Serverbound<?> sb : NeroLogisticsNetwork.serverbound()) {
            registerServerbound(registrar, sb);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(PayloadRegistrar registrar,
            NeroLogisticsNetwork.Clientbound<T> cb) {
        registrar.playToClient(cb.type(), cb.codec(),
                (payload, context) -> context.enqueueWork(() -> cb.handler().accept(payload)));
    }

    private static <T extends CustomPacketPayload> void registerServerbound(PayloadRegistrar registrar,
            NeroLogisticsNetwork.Serverbound<T> sb) {
        registrar.playToServer(sb.type(), sb.codec(),
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer serverPlayer) {
                        sb.handler().accept(payload, serverPlayer);
                    }
                }));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
