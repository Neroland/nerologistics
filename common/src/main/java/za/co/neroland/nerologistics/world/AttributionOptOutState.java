package za.co.neroland.nerologistics.world;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerologistics.NeroLogisticsCommon;

/**
 * Players who opted out of per-player throughput attribution with {@code /nerologistics privacy optout}.
 * With attribution ON server-wide, an opted-out player's shipments count only in the aggregate
 * (per-dimension) figures: no attribution record, and no owner UUID on the ports they place.
 *
 * <p><b>Data minimisation:</b> bare UUIDs only (no name, no timestamps), kept solely to honour the
 * opt-out; stored on the overworld through the guarded {@link SavedDataRecovery} accessor. Core's
 * {@code PlayerDataErasure} removes the entry along with everything else NeroLogistics holds for the
 * player, so after an erasure request a player who wants to stay opted out runs the command again.</p>
 */
public final class AttributionOptOutState extends SavedData {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "attribution_opt_out");

    public static final SavedDataType<AttributionOptOutState> TYPE =
            new SavedDataType<>(ID, AttributionOptOutState::new, codec(), null);

    private final Set<String> optedOut = new HashSet<>();

    public AttributionOptOutState() {
    }

    public static AttributionOptOutState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, AttributionOptOutState::new, ID.toString());
    }

    public boolean isOptedOut(UUID player) {
        return this.optedOut.contains(player.toString());
    }

    /** Returns true when the state changed. */
    public boolean setOptedOut(UUID player, boolean out) {
        boolean changed = out ? this.optedOut.add(player.toString()) : this.optedOut.remove(player.toString());
        if (changed) {
            setDirty();
        }
        return changed;
    }

    private static Codec<AttributionOptOutState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.listOf().optionalFieldOf("opted_out", List.of())
                        .forGetter(s -> List.copyOf(s.optedOut))
        ).apply(inst, AttributionOptOutState::fromData));
    }

    private static AttributionOptOutState fromData(List<String> optedOut) {
        AttributionOptOutState state = new AttributionOptOutState();
        state.optedOut.addAll(optedOut);
        return state;
    }
}
