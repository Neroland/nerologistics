package za.co.neroland.nerologistics.platform;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerologistics.NeroLogisticsCommon;

/**
 * Fabric fluid bridge. Owns the shared {@code nerologistics:fluid}
 * {@link BlockApiLookup} over Core's {@link NeroFluidStorage}; ducts and tanks
 * register their block-entities against {@link #FLUID}. Registered via
 * {@code META-INF/services}. Mirrors Core's {@code FabricEnergyLookup}.
 */
public final class FabricFluidLookup implements IFluidLookup {

    /** The NeroLogistics fluid lookup. Endpoints register {@link NeroFluidStorage} providers for it. */
    public static final BlockApiLookup<NeroFluidStorage, Direction> FLUID =
            BlockApiLookup.get(
                    Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "fluid"),
                    NeroFluidStorage.class, Direction.class);

    @Nullable
    @Override
    public NeroFluidStorage find(Level level, BlockPos pos, @Nullable Direction side) {
        return FLUID.find(level, pos, side);
    }
}
