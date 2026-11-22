package za.co.neroland.nerologistics.privacy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.PlayerDataExport;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.world.AttributionOptOutState;
import za.co.neroland.nerologistics.world.QuestPerksState;

/**
 * NeroLogistics' part of Neroland Core's {@code /neroland data export}: everything it keeps about one player,
 * built in memory, never written to disk or logged. NeroLogistics records no owners for networks, drives or
 * drones; what it does keep per player is the opt-in shipment attribution, the attribution opt-out, the quest
 * perks (with a saved cell preset) and, on rocket cargo ports, the opt-in owner and the dispatcher.
 */
public final class LogisticsDataExport {

    private LogisticsDataExport() {
    }

    /** Registers with Core at common init. */
    public static void register() {
        PlayerDataExport.register(NeroLogisticsCommon.MOD_ID, LogisticsDataExport::export);
    }

    /** Core's exporter over the live stores. Server thread; reads only, loads no chunks. */
    public static @Nullable JsonObject export(MinecraftServer server, UUID player) {
        QuestPerksState.Perks perks = QuestPerksState.get(server).perksOf(player);
        JsonElement preset = null;
        if (perks.presetSlot() && perks.preset().isPresent()) {
            preset = za.co.neroland.nerologistics.storage.CellSettings.CODEC
                    .encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), perks.preset().get())
                    .result().orElse(null);
        }
        return build(LogisticsMetrics.attributionOf(player), AttributionOptOutState.get(server).isOptedOut(player),
                perks.dashboardBadge(), perks.presetSlot(), perks.preset().isPresent(), preset,
                RocketCargoPortBlockEntity.loadedPortsOf(player));
    }

    /** The document from plain values; {@code null} when NeroLogistics holds nothing for the player. Plain JVM. */
    public static @Nullable JsonObject build(@Nullable long[] attribution, boolean optedOut, boolean badge,
            boolean presetSlot, boolean presetSaved, @Nullable JsonElement preset,
            List<RocketCargoPortBlockEntity.PortRole> ports) {
        if (attribution == null && !optedOut && !badge && !presetSlot && !presetSaved && ports.isEmpty()) {
            return null;
        }
        JsonObject root = new JsonObject();
        JsonObject attr = new JsonObject();
        attr.addProperty("opted_out", optedOut);
        if (attribution != null && attribution.length >= 2) {
            attr.addProperty("last_active", LocalDate.ofEpochDay(attribution[0]).toString());
            attr.addProperty("shipments_attributed", attribution[1]);
        }
        root.add("shipment_attribution", attr);

        JsonObject quest = new JsonObject();
        quest.addProperty("dashboard_badge", badge);
        quest.addProperty("preset_slot_unlocked", presetSlot);
        quest.addProperty("preset_saved", presetSaved);
        if (preset != null) {
            quest.add("preset", preset);
        }
        root.add("quest_perks", quest);

        JsonArray portRows = new JsonArray();
        for (RocketCargoPortBlockEntity.PortRole port : ports) {
            JsonObject row = new JsonObject();
            row.addProperty("dimension", port.dimension().toString());
            row.addProperty("x", port.pos().getX());
            row.addProperty("y", port.pos().getY());
            row.addProperty("z", port.pos().getZ());
            row.addProperty("you_are_owner", port.owner());
            row.addProperty("you_are_dispatcher", port.dispatcher());
            portRows.add(row);
        }
        root.add("rocket_cargo_ports_loaded_now", portRows);

        JsonObject notes = new JsonObject();
        notes.addProperty("ports", "Only rocket cargo ports in loaded chunks are listed: each port keeps its owner "
                + "and dispatcher in its own block data, and an export does not load the world. An erasure request "
                + "reaches every port, loaded or not.");
        notes.addProperty("attribution", "Shipment attribution is opt-in, held in memory only and pruned after the "
                + "configured retention; the per-dimension totals never name anyone.");
        root.add("notes", notes);
        root.addProperty("exported_at", Instant.now().toString());
        return root;
    }
}
