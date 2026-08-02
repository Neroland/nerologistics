package za.co.neroland.nerologistics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import net.minecraft.core.registries.BuiltInRegistries;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.storage.ItemKey;
import za.co.neroland.nerologistics.storage.NetworkStorageIndex;

/**
 * Server &rarr; client: the aggregated contents of the storage network behind an open storage
 * terminal menu, keyed by container id. Sent on menu open and then re-sent at most every
 * {@code terminalResyncTicks} (default 10t) <b>and only when the snapshot actually changed</b>
 * (the menu compares against the last copy it sent). The client filters/sorts this list locally —
 * search and sort never round-trip.
 *
 * <p>Item lines carry the exact count-1 prototype stack (item + data components — the same
 * identity {@link ItemKey} uses server-side) plus a var-long total; fluid lines carry the fluid's
 * registry id plus total mB. Line count is capped at the index's own
 * {@link NetworkStorageIndex#MAX_TRACKED_TYPES} (4096), which keeps the payload comfortably under
 * the vanilla 1 MiB custom-payload ceiling even in degenerate component-heavy worlds.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> carries only a container id, a revision counter and block-scoped
 * inventory aggregates — no player identity, never logged, never persisted.
 *
 * @param containerId the open menu's container id (the client mailbox key)
 * @param revision    monotonically increasing per-menu revision (stale payloads lose)
 * @param items       aggregated item lines (count-1 prototype + total), server truth
 * @param fluids      aggregated fluid lines (registry id + total mB), server truth
 */
public record StorageTerminalContentsPayload(int containerId, int revision,
        List<ItemLine> items, List<FluidLine> fluids) implements CustomPacketPayload {

    /** One aggregated item type: exact count-1 prototype + network-wide total. */
    public record ItemLine(ItemStack prototype, long count) {
    }

    /** One aggregated fluid: registry id + network-wide total mB. */
    public record FluidLine(Identifier fluidId, long amount) {
    }

    /** Hard cap on lines per payload — mirrors the index's own distinct-type cap. */
    public static final int MAX_LINES = NetworkStorageIndex.MAX_TRACKED_TYPES;

    public static final Type<StorageTerminalContentsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "storage_terminal_contents"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageTerminalContentsPayload> STREAM_CODEC =
            StreamCodec.of(StorageTerminalContentsPayload::write, StorageTerminalContentsPayload::read);

    /** Build a payload from live index snapshots (server side; the maps are not retained). */
    public static StorageTerminalContentsPayload of(int containerId, int revision,
            Map<ItemKey, Long> itemSnapshot, Map<Fluid, Long> fluidSnapshot) {
        List<ItemLine> items = new ArrayList<>(Math.min(itemSnapshot.size(), MAX_LINES));
        for (Map.Entry<ItemKey, Long> entry : itemSnapshot.entrySet()) {
            if (items.size() >= MAX_LINES) {
                break;
            }
            if (entry.getValue() > 0) {
                items.add(new ItemLine(entry.getKey().prototype(), entry.getValue()));
            }
        }
        List<FluidLine> fluids = new ArrayList<>(Math.min(fluidSnapshot.size(), MAX_LINES));
        for (Map.Entry<Fluid, Long> entry : fluidSnapshot.entrySet()) {
            if (fluids.size() >= MAX_LINES) {
                break;
            }
            if (entry.getValue() > 0) {
                fluids.add(new FluidLine(BuiltInRegistries.FLUID.getKey(entry.getKey()), entry.getValue()));
            }
        }
        return new StorageTerminalContentsPayload(containerId, revision, items, fluids);
    }

    private static void write(RegistryFriendlyByteBuf buf, StorageTerminalContentsPayload payload) {
        buf.writeVarInt(payload.containerId);
        buf.writeVarInt(payload.revision);
        int itemCount = Math.min(MAX_LINES, payload.items.size());
        buf.writeVarInt(itemCount);
        for (int i = 0; i < itemCount; i++) {
            ItemLine line = payload.items.get(i);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, line.prototype());
            buf.writeVarLong(Math.max(0, line.count()));
        }
        int fluidCount = Math.min(MAX_LINES, payload.fluids.size());
        buf.writeVarInt(fluidCount);
        for (int i = 0; i < fluidCount; i++) {
            FluidLine line = payload.fluids.get(i);
            buf.writeIdentifier(line.fluidId());
            buf.writeVarLong(Math.max(0, line.amount()));
        }
    }

    private static StorageTerminalContentsPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int revision = buf.readVarInt();
        int itemCount = Math.min(MAX_LINES, buf.readVarInt());
        List<ItemLine> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            ItemStack prototype = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            long count = buf.readVarLong();
            if (!prototype.isEmpty() && count > 0) {
                items.add(new ItemLine(prototype, count));
            }
        }
        int fluidCount = Math.min(MAX_LINES, buf.readVarInt());
        List<FluidLine> fluids = new ArrayList<>(fluidCount);
        for (int i = 0; i < fluidCount; i++) {
            Identifier id = buf.readIdentifier();
            long amount = buf.readVarLong();
            if (amount > 0) {
                fluids.add(new FluidLine(id, amount));
            }
        }
        return new StorageTerminalContentsPayload(containerId, revision, items, fluids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
