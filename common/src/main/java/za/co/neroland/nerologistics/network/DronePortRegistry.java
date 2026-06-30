package za.co.neroland.nerologistics.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Per-dimension directory of {@code DronePortBlockEntity} positions, so an export port can find the
 * import ports to ship to without scanning the world. Membership is cached and only touched on
 * place/break (mirrors {@link WirelessRegistry}). Server-side only.
 */
public final class DronePortRegistry {

    private static final Map<ResourceKey<Level>, List<BlockPos>> LEVELS = new HashMap<>();

    private DronePortRegistry() {
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

    /** A snapshot of the drone-port positions registered in this dimension. */
    public static List<BlockPos> membersOf(Level level) {
        return new ArrayList<>(members(level));
    }
}
