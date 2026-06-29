package za.co.neroland.nerologistics.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerologistics.NeroLogisticsCommon;

/**
 * NeoForge fluid bridge. Owns the shared {@code nerologistics:fluid}
 * {@link BlockCapability} over Core's {@link NeroFluidStorage}; ducts and tanks register
 * their block-entities against {@link #FLUID} during {@code RegisterCapabilitiesEvent}
 * (wired in Stage 2 once endpoints exist). Registered via {@code META-INF/services}.
 * Mirrors Core's {@code NeoForgeEnergyLookup}.
 */
public final class NeoForgeFluidLookup implements IFluidLookup {

    /** The NeroLogistics fluid capability. Endpoints register {@link NeroFluidStorage} providers for it. */
    public static final BlockCapability<NeroFluidStorage, Direction> FLUID =
            BlockCapability.createSided(
                    Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "fluid"),
                    NeroFluidStorage.class);

    @Nullable
    @Override
    public NeroFluidStorage find(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(FLUID, pos, side);
    }
}
