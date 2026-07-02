package za.co.neroland.nerologistics.ship;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An in-transit shipment (rocket cargo or train haul): its payload, the source and destination
 * endpoints (dimension + block position), and the departure/arrival game ticks. Held durably in
 * {@link ShipmentState} for the transit duration — no chunks are kept loaded meanwhile, and a
 * shipment in flight survives a server restart (arrival ticks are absolute overworld game time,
 * which persists, so timers resume exactly where they left off).
 *
 * <p>POPIA/GDPR: a manifest stores payload, dimensions, positions and ticks only — never a player
 * name or UUID.
 */
public record CargoManifest(List<ItemStack> items, ResourceKey<Level> fromDim, BlockPos fromPos,
        ResourceKey<Level> destDim, BlockPos destPos, long departureTick, long arrivalTick) {

    /** Codec for durable persistence via {@link ShipmentState}; item components ride along. */
    public static final Codec<CargoManifest> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(CargoManifest::items),
            Level.RESOURCE_KEY_CODEC.fieldOf("from_dim").forGetter(CargoManifest::fromDim),
            BlockPos.CODEC.fieldOf("from_pos").forGetter(CargoManifest::fromPos),
            Level.RESOURCE_KEY_CODEC.fieldOf("dest_dim").forGetter(CargoManifest::destDim),
            BlockPos.CODEC.fieldOf("dest_pos").forGetter(CargoManifest::destPos),
            Codec.LONG.fieldOf("departure").forGetter(CargoManifest::departureTick),
            Codec.LONG.fieldOf("arrival").forGetter(CargoManifest::arrivalTick)
    ).apply(inst, CargoManifest::new));
}
