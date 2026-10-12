package za.co.neroland.nerologistics.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.storage.CellSettings;

/**
 * Per-player cosmetic perks unlocked by quest rewards (the {@code nerologistics:hook/*} record gates a quest
 * pack may name — see {@code quest.QuestHooks}). Two perks exist:
 *
 * <ul>
 *   <li><b>Dashboard badge</b> — a cosmetic line on that player's Logistics Dashboard read-out.</li>
 *   <li><b>Cell preset slot</b> — one saved partition/priority preset the player can stamp onto any storage
 *       cell from the cell configuration screen. The preset itself (ghost item ids + priority) is stored
 *       here once the player saves one.</li>
 * </ul>
 *
 * <p><b>POPIA/GDPR.</b> Keyed by UUID only (never a name); each entry holds two booleans and, at most, the
 * player's own saved cell preset. Nothing here is ever logged or shown to another player. Stored on the
 * overworld through the guarded {@link SavedDataRecovery} accessor and removed in full by Core's
 * {@code PlayerDataErasure} (see {@link #erase}); the backup file is refreshed right after an erasure.
 * Neither perk gates access to anything — a player without them loses only the cosmetic line and the
 * Save/Load preset buttons.</p>
 */
public final class QuestPerksState extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "quest_perks");

    /** One player's perks. Immutable; mutations replace the entry. */
    public record Perks(boolean dashboardBadge, boolean presetSlot, Optional<CellSettings> preset) {
        static final Perks NONE = new Perks(false, false, Optional.empty());

        static final Codec<Perks> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("dashboard_badge", false).forGetter(Perks::dashboardBadge),
                Codec.BOOL.optionalFieldOf("preset_slot", false).forGetter(Perks::presetSlot),
                CellSettings.CODEC.optionalFieldOf("preset").forGetter(Perks::preset)
        ).apply(inst, Perks::new));

        boolean isEmpty() {
            return !this.dashboardBadge && !this.presetSlot && this.preset.isEmpty();
        }
    }

    public static final SavedDataType<QuestPerksState> TYPE =
            new SavedDataType<>(ID, QuestPerksState::new, codec(), null);

    private final Map<String, Perks> perks = new HashMap<>();

    public QuestPerksState() {
    }

    public static QuestPerksState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, QuestPerksState::new, ID.toString());
    }

    private Perks of(UUID player) {
        return this.perks.getOrDefault(player.toString(), Perks.NONE);
    }

    private void put(UUID player, Perks value) {
        if (value.isEmpty()) {
            this.perks.remove(player.toString());
        } else {
            this.perks.put(player.toString(), value);
        }
        setDirty();
    }

    public boolean hasDashboardBadge(UUID player) {
        return of(player).dashboardBadge();
    }

    public boolean hasPresetSlot(UUID player) {
        return of(player).presetSlot();
    }

    /** The player's saved preset, or {@code null} when the slot is locked or still empty. */
    @Nullable
    public CellSettings preset(UUID player) {
        Perks p = of(player);
        return p.presetSlot() ? p.preset().orElse(null) : null;
    }

    /** Idempotent: grants the badge (a repeat is a no-op). */
    public void grantDashboardBadge(UUID player) {
        Perks p = of(player);
        if (!p.dashboardBadge()) {
            put(player, new Perks(true, p.presetSlot(), p.preset()));
        }
    }

    /** Idempotent: unlocks the preset slot (a repeat is a no-op; an existing preset is kept). */
    public void unlockPresetSlot(UUID player) {
        Perks p = of(player);
        if (!p.presetSlot()) {
            put(player, new Perks(p.dashboardBadge(), true, p.preset()));
        }
    }

    /** Save {@code settings} into the player's slot. Returns false when the slot is locked. */
    public boolean savePreset(UUID player, CellSettings settings) {
        Perks p = of(player);
        if (!p.presetSlot()) {
            return false;
        }
        put(player, new Perks(p.dashboardBadge(), true, Optional.of(settings)));
        return true;
    }

    /** POPIA/GDPR erasure: drop everything held for {@code player}. Returns true when something was removed. */
    public boolean erase(UUID player) {
        boolean removed = this.perks.remove(player.toString()) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    private static Codec<QuestPerksState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Codec.unboundedMap(Codec.STRING, Perks.CODEC).optionalFieldOf("players", Map.of())
                        .forGetter(s -> Map.copyOf(s.perks))
        ).apply(inst, QuestPerksState::fromData));
    }

    private static QuestPerksState fromData(Map<String, Perks> perks) {
        QuestPerksState state = new QuestPerksState();
        state.perks.putAll(perks);
        return state;
    }
}
