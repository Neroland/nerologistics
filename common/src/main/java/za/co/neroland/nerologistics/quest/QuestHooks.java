package za.co.neroland.nerologistics.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.CoreEvents;
import za.co.neroland.nerolandcore.progression.GateEvents.GateChange;
import za.co.neroland.nerolandcore.progression.GateScope;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.world.QuestPerksState;

/**
 * Listens for the two <b>hook record gates</b> NeroLogistics ships for quest packs and turns each into a
 * per-player perk in {@link QuestPerksState}:
 *
 * <ul>
 *   <li>{@code nerologistics:hook/dashboard_badge} → cosmetic badge line on the Logistics Dashboard.</li>
 *   <li>{@code nerologistics:hook/cell_preset} → unlocks the saved cell preset slot.</li>
 * </ul>
 *
 * <p>A quest mod opens the gate for the completing player through Neroland Core (online or offline);
 * this class only consumes Core's progression events — NeroLogistics has no dependency on any quest mod,
 * and the gates are plain data under {@code data/nerologistics/neroland_gates/hook/}. Both perks are one-off
 * unlocks written by UUID straight into the saved store, so an offline completion needs no on-join step and
 * the gate is left open (re-arming is unnecessary; a repeat open is idempotent). The gates gate nothing and
 * are never checked for access.</p>
 *
 * <p>The listener runs on the server thread but carries no server handle, so the one driving the per-tick
 * shipment work is kept here ({@link #tick}); a change that arrives before the first tick is queued and
 * applied on it. The player UUID is never logged (POPIA/GDPR).</p>
 */
public final class QuestHooks {

    public static final Identifier DASHBOARD_BADGE =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "hook/dashboard_badge");
    public static final Identifier CELL_PRESET =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "hook/cell_preset");

    @Nullable
    private static MinecraftServer server;
    /** Gate opens seen before a server handle existed; drained on the next tick. Server thread only. */
    private static final List<GateChange> PENDING = new ArrayList<>();

    private QuestHooks() {
    }

    /** Call once from common init. */
    public static void init() {
        CoreEvents.onProgression(change -> {
            if (!change.open() || change.scope() != GateScope.PLAYER || !isHook(change.gate())) {
                return;
            }
            MinecraftServer current = server;
            if (current == null) {
                PENDING.add(change);
                return;
            }
            apply(current, change);
        });
    }

    /** Once per server tick: keeps the server handle current and drains anything queued before it existed. */
    public static void tick(MinecraftServer current) {
        server = current;
        if (!PENDING.isEmpty()) {
            List<GateChange> queued = new ArrayList<>(PENDING);
            PENDING.clear();
            for (GateChange change : queued) {
                apply(current, change);
            }
        }
    }

    /** Server-stopped reset: forget the handle and anything queued for the old world. */
    public static void clearAll() {
        server = null;
        PENDING.clear();
    }

    private static boolean isHook(Identifier gate) {
        return DASHBOARD_BADGE.equals(gate) || CELL_PRESET.equals(gate);
    }

    private static void apply(MinecraftServer current, GateChange change) {
        UUID player;
        try {
            player = UUID.fromString(change.target());
        } catch (IllegalArgumentException e) {
            return; // not a player target
        }
        QuestPerksState perks = QuestPerksState.get(current);
        if (DASHBOARD_BADGE.equals(change.gate())) {
            perks.grantDashboardBadge(player);
        } else if (CELL_PRESET.equals(change.gate())) {
            perks.unlockPresetSlot(player);
        }
    }
}
