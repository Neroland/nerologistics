package za.co.neroland.nerologistics.ship;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

/**
 * The launching side of a shipment: the port's level, position and channel, plus — for the Nerospace
 * provider only — the dispatcher the port ships <em>as</em>. The dispatcher UUID travels only as far as
 * the provider call; it is never logged or put in a {@link FlightView}.
 *
 * @param level      the port's level
 * @param pos        the port's position
 * @param channel    the port's route channel (stub provider only)
 * @param dispatcher the player the port acts for, or {@code null} when nobody configured it
 */
public record ShipOrigin(ServerLevel level, BlockPos pos, int channel, @Nullable UUID dispatcher) {
}
