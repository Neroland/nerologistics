package za.co.neroland.nerologistics.map;

import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import za.co.neroland.nerologistics.network.DronePortRegistry;
import za.co.neroland.nerologistics.network.TrainStationRegistry;

/** The live source: the per-dimension registries the ports and stations keep while loaded. */
final class ServerMapSource implements NeroLogisticsMapSource {

    @Override
    public Collection<BlockPos> dronePorts(@Nullable MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server == null ? null : server.getLevel(dimension);
        return level == null ? List.of() : DronePortRegistry.membersOf(level);
    }

    @Override
    public Collection<BlockPos> trainStations(@Nullable MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server == null ? null : server.getLevel(dimension);
        return level == null ? List.of() : TrainStationRegistry.membersOf(level);
    }
}
