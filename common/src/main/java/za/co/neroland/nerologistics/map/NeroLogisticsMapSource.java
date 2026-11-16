package za.co.neroland.nerologistics.map;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * Where {@link NeroLogisticsMapLayers} reads its data: the in-memory port and station registries. The provider
 * owns every visibility decision, so the rules can be tested against a seeded fake. Every method answers an
 * empty collection when {@code server} is {@code null} or the dimension is not loaded. Never loads a chunk.
 */
public interface NeroLogisticsMapSource {

    /** Drone-port positions registered in {@code dimension}. */
    Collection<BlockPos> dronePorts(@Nullable MinecraftServer server, ResourceKey<Level> dimension);

    /** Train-station positions registered in {@code dimension}. */
    Collection<BlockPos> trainStations(@Nullable MinecraftServer server, ResourceKey<Level> dimension);
}
