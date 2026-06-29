package za.co.neroland.nerologistics.ship;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An in-transit cross-dimension shipment: its payload, the destination dimension + port position, and
 * the server game-tick it materialises at. Held in memory by {@link ShipmentManager} for the transit
 * duration (no chunks are kept loaded meanwhile).
 */
public record CargoManifest(List<ItemStack> items, ResourceKey<Level> destDim, BlockPos destPos, long arrivalTick) {
}
