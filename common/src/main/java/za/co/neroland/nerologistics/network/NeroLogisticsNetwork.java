package za.co.neroland.nerologistics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.nerologistics.platform.INetworkHelper;

/**
 * NeroLogistics' cross-loader networking registry — a direct port of Nerotech's
 * {@code NeroTechNetwork}, one repo over: payloads are declared here once (type + stream codec +
 * common-safe handler); each loader module iterates the lists and wires them to its own networking
 * API (NeoForge {@code PayloadRegistrar}, Forge {@code ChannelBuilder}, Fabric
 * {@code PayloadTypeRegistry} + receivers). Sending goes through {@link INetworkHelper#INSTANCE}.
 *
 * <p>NeroLogistics cannot simply add its payloads to Core's {@code CoreNetwork}: Core's Forge and
 * Fabric modules drain those lists during Core's own bootstrap, and Core — as a hard dependency —
 * always initialises before NeroLogistics, so anything added from
 * {@code NeroLogisticsCommon.init()} would arrive after Core's channel is already built. Same
 * reasoning as NeroLogistics' own {@code RegistrationProvider} seam.
 *
 * <p>V1 registers the Stage-15 storage-terminal pair: the clientbound
 * {@link StorageTerminalContentsPayload} (network contents while a terminal menu is open, keyed by
 * container id into the {@link ClientStorageTerminal} mailbox) and the serverbound
 * {@link StorageTerminalActionPayload} (grid-interaction intents, fully re-validated server-side
 * against the sender's open menu — never trusted indices, always exact item prototypes).
 */
public final class NeroLogisticsNetwork {

    /** A server → client payload + the client-side handler that consumes it. */
    public record Clientbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
    }

    /** A client → server payload + the server-side handler (with the sending player). */
    public record Serverbound<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
    }

    private static final List<Clientbound<?>> CLIENTBOUND = new ArrayList<>();
    private static final List<Serverbound<?>> SERVERBOUND = new ArrayList<>();

    private NeroLogisticsNetwork() {
    }

    public static <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {
        CLIENTBOUND.add(new Clientbound<>(type, codec, handler));
    }

    public static <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
        SERVERBOUND.add(new Serverbound<>(type, codec, handler));
    }

    public static List<Clientbound<?>> clientbound() {
        return CLIENTBOUND;
    }

    public static List<Serverbound<?>> serverbound() {
        return SERVERBOUND;
    }

    /** Called from common init so the payload list exists before each loader registers it. */
    public static void init() {
        clientbound(StorageTerminalContentsPayload.TYPE, StorageTerminalContentsPayload.STREAM_CODEC,
                ClientStorageTerminal::accept);
        serverbound(StorageTerminalActionPayload.TYPE, StorageTerminalActionPayload.STREAM_CODEC,
                StorageTerminalActionPayload::handle);
    }

    /** Server → one client, through the loader's send seam. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        INetworkHelper.INSTANCE.sendToPlayer(player, payload);
    }

    /** Client → server, through the loader's send seam (call only on the physical client). */
    public static void sendToServer(CustomPacketPayload payload) {
        INetworkHelper.INSTANCE.sendToServer(payload);
    }
}
