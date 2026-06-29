package za.co.neroland.nerologistics.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Default {@link RouteProvider} used until a Nerospace-backed provider is registered: every loaded
 * dimension is a destination. Keeps cross-dimension shipping fully testable standalone (e.g. Overworld
 * &harr; Nether) without any Nerospace dependency.
 */
public final class StubRouteProvider implements RouteProvider {

    @Override
    public List<RouteDestination> destinations(MinecraftServer server) {
        List<RouteDestination> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            out.add(new RouteDestination(key, key.location().getPath()));
        }
        return out;
    }
}
