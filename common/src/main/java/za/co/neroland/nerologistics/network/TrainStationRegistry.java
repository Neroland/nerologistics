package za.co.neroland.nerologistics.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Per-dimension directory of train-station positions, so a load station can find the unload stations on
 * its line without scanning the world. Cached, only touched on place/break (mirrors
 * {@link DronePortRegistry}). Server-side only.
 */
public final class TrainStationRegistry {

    private static final Map<ResourceKey<Level>, List<BlockPos>> LEVELS = new HashMap<>();

    private TrainStationRegistry() {
    }

    private static List<BlockPos> members(Level level) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
    }

    public static void onPlaced(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        List<BlockPos> list = members(level);
        BlockPos key = pos.immutable();
        if (!list.contains(key)) {
            list.add(key);
        }
    }

    public static void onRemoved(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        members(level).remove(pos.immutable());
    }

    /** A snapshot of the train-station positions registered in this dimension. */
    public static List<BlockPos> membersOf(Level level) {
        return new ArrayList<>(members(level));
    }
}
