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
 * POPIA/GDPR erasure tombstones: the set of player UUIDs whose data-erasure request has been
 * processed. A {@code RocketCargoPortBlockEntity} whose chunk was <em>unloaded</em> when the erasure
 * ran still carries the owner UUID in its NBT; each port consults this set on its first server tick
 * after loading and scrubs itself if its owner was erased — so an erasure request is guaranteed to
 * reach every port eventually, without force-loading the whole world at erasure time.
 *
 * <p><b>Data minimisation:</b> the set holds only the bare UUID (no name, no timestamps, no
 * activity), exists solely to complete the erasure the player asked for (a purpose GDPR/POPIA
 * explicitly permit records of), and is stored on the overworld via the guarded
 * {@link SavedDataRecovery} accessor. UUIDs are serialised as strings, matching the ecosystem's
 * existing SavedData precedent (Nerospace's {@code StationRegistry}).
 */
public final class ErasedOwnersState extends SavedData {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "erased_owners");

    public static final SavedDataType<ErasedOwnersState> TYPE =
            new SavedDataType<>(ID, ErasedOwnersState::new, codec(), null);

    /** Canonical string forms of the erased UUIDs. */
    private final Set<String> erased = new HashSet<>();

    public ErasedOwnersState() {
    }

    /** Guarded accessor (corrupt file recovers via backup-then-fresh instead of crashing). */
    public static ErasedOwnersState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, ErasedOwnersState::new, ID.toString());
    }

    /** Record an erasure request; returns true if the UUID was not already tombstoned. */
    public boolean add(UUID player) {
        boolean added = this.erased.add(player.toString());
        if (added) {
            setDirty();
        }
        return added;
    }

    /** Whether {@code owner}'s erasure has been requested (port scrubs itself on load if so). */
    public boolean contains(UUID owner) {
        return this.erased.contains(owner.toString());
    }

    // --- persistence --------------------------------------------------------

    private static Codec<ErasedOwnersState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.listOf().optionalFieldOf("erased", List.of())
                        .forGetter(s -> List.copyOf(s.erased))
        ).apply(inst, ErasedOwnersState::fromData));
    }

    private static ErasedOwnersState fromData(List<String> erased) {
        ErasedOwnersState state = new ErasedOwnersState();
        state.erased.addAll(erased);
        return state;
    }
}
