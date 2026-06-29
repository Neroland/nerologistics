package za.co.neroland.nerologistics.ship;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A cross-dimension shipping destination: a dimension plus a display name. Produced by a
 * {@link RouteProvider} — the stub derives these from the server's dimensions; a Nerospace-backed
 * provider would derive them from its planet/station catalog.
 */
public record RouteDestination(ResourceKey<Level> dimension, String name) {

    public Identifier id() {
        return this.dimension.location();
    }
}
