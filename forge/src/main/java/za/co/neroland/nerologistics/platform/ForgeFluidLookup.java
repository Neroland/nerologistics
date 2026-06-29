package za.co.neroland.nerologistics.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;

/**
 * Forge fluid bridge. Owns the shared {@link NeroFluidStorage} capability; ducts and
 * tanks attach providers to their block-entities via {@code AttachCapabilitiesEvent}
 * (wired in Stage 2 once endpoints exist). Registered via {@code META-INF/services}.
 * Mirrors Core's {@code ForgeEnergyLookup}.
 */
public final class ForgeFluidLookup implements IFluidLookup {

    /** The NeroLogistics fluid capability. Endpoints attach {@link NeroFluidStorage} providers for it. */
    public static final Capability<NeroFluidStorage> FLUID =
            CapabilityManager.get(new CapabilityToken<>() { });

    @Nullable
    @Override
    public NeroFluidStorage find(Level level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        return be.getCapability(FLUID, side).orElse(null);
    }
}
