package za.co.neroland.nerologistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.menu.StorageTerminalMenu;

/**
 * Client &rarr; server intent for the storage terminal grid — "do {@code action} against the
 * network behind my open menu {@code containerId}", mirroring Nerotech's
 * {@code MachinePresetPayload} flow. The screen's grid is <b>not</b> made of real slots, so every
 * grid interaction rides this payload instead of vanilla slot clicks.
 *
 * <p><b>Never trusted:</b> the client references an item line by its exact count-1 prototype
 * stack (item + data components — the same identity the server's {@code ItemKey} uses), never by
 * grid index or snapshot position, so a stale/reordered client view can at worst request an item
 * that is no longer stored (the extract simply moves 0). The server re-validates everything in
 * {@link #handle}: the container id must match the sender's open menu, that menu must be a
 * {@link StorageTerminalMenu} whose {@code stillValid} holds, and all amounts are recomputed
 * server-side from live network state inside the menu.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> carries only a container id, an action ordinal, an item
 * prototype and a fluid id — no player identity, never logged.
 *
 * @param containerId the open menu's container id
 * @param action      what to do (see {@link Action})
 * @param item        exact count-1 prototype for the extract actions ({@link ItemStack#EMPTY} otherwise)
 * @param fluidId     target fluid for the bucket actions ({@code null} otherwise)
 */
public record StorageTerminalActionPayload(int containerId, Action action,
        ItemStack item, @Nullable Identifier fluidId) implements CustomPacketPayload {

    /** Grid interactions. All amounts are computed server-side; the client sends intent only. */
    public enum Action {
        /** Empty cursor, left-click an item line: extract up to one stack to the cursor. */
        EXTRACT_STACK,
        /** Empty cursor, right-click an item line: extract half a stack (min 1) to the cursor. */
        EXTRACT_HALF,
        /** Shift-left-click an item line: extract up to one stack straight into the inventory. */
        EXTRACT_TO_INVENTORY,
        /** Carried stack, left-click the grid: insert the whole carried stack into the network. */
        INSERT_CARRIED,
        /** Carried stack, right-click the grid: insert one item off the carried stack. */
        INSERT_CARRIED_ONE,
        /** Carried empty bucket, click a fluid line: fill it with 1000 mB of {@code fluidId}. */
        FILL_BUCKET,
        /** Carried full bucket, click the fluid grid: drain it (1000 mB) into the network. */
        DRAIN_BUCKET;

        static final Action[] VALUES = values();
    }

    public static final Type<StorageTerminalActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "storage_terminal_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageTerminalActionPayload> STREAM_CODEC =
            StreamCodec.of(StorageTerminalActionPayload::write, StorageTerminalActionPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, StorageTerminalActionPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeByte(payload.action.ordinal());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.item);
        buf.writeBoolean(payload.fluidId != null);
        if (payload.fluidId != null) {
            buf.writeIdentifier(payload.fluidId);
        }
    }

    private static StorageTerminalActionPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int ordinal = buf.readUnsignedByte();
        Action action = Action.VALUES[Math.min(ordinal, Action.VALUES.length - 1)];
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        Identifier fluidId = buf.readBoolean() ? buf.readIdentifier() : null;
        return new StorageTerminalActionPayload(containerId, action, item, fluidId);
    }

    /** Server-side handler: validate the intent against the sender's open menu, then apply. */
    public static void handle(StorageTerminalActionPayload payload, ServerPlayer player) {
        if (player.containerMenu == null || player.containerMenu.containerId != payload.containerId) {
            return; // stale intent — the menu already closed or changed
        }
        if (!(player.containerMenu instanceof StorageTerminalMenu menu)) {
            return;
        }
        if (!menu.stillValid(player)) {
            return;
        }
        menu.handleAction(player, payload);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
