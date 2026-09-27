package za.co.neroland.nerologistics.ship;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One destination a rocket cargo port can ship to, as listed by the active {@link RouteProvider}. Either
 * a whole dimension (the stub provider) or a specific Nerospace Cargo Pad / station — both share the
 * shape, and {@link #key()} is the stable identity the port persists.
 *
 * <p>Snapshot only: providers rebuild these per query. Never carries a player UUID or another player's
 * pad position — the dimension is enough for the dashboard's counters.</p>
 *
 * @param key       the stable identity the port saves ({@code pad:<id>} or {@code dim:<id>})
 * @param dimension the dimension the destination stands in
 * @param name      player-facing label (dimension path, pad name tag, or station name)
 * @param station   whether this is a Nerospace station endpoint
 */
public record RouteDestination(DestinationKey key, ResourceKey<Level> dimension, String name, boolean station) {

    /** A whole-dimension destination (stub provider). */
    public static RouteDestination ofDimension(ResourceKey<Level> dimension) {
        return new RouteDestination(DestinationKey.dimension(dimension.identifier().toString()), dimension,
                dimension.identifier().getPath(), false);
    }

    /** A Nerospace pad or station destination. */
    public static RouteDestination ofPad(int padId, String name, ResourceKey<Level> dimension, boolean station) {
        return new RouteDestination(DestinationKey.pad(padId), dimension, name, station);
    }

    /** The label shown in chat — stations are prefixed "Station:". */
    public Component displayName() {
        return this.station
                ? Component.translatable("block.nerologistics.rocket_cargo_port.destination.station", this.name)
                : Component.literal(this.name);
    }
}
