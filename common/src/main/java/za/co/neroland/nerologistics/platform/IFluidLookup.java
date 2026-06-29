package za.co.neroland.nerologistics.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.platform.Services;

/**
 * Query side of the fluid seam: find the {@link NeroFluidStorage} exposed by the block
 * at {@code pos} on {@code side}. The fluid analogue of Core's
 * {@code platform.EnergyLookup}; reuses Core's neutral {@link NeroFluidStorage} surface
 * (rather than a new type) so Nero fluid handling stays one vocabulary across the
 * ecosystem. Each loader implements it over its own lookup mechanism (NeoForge
 * {@code BlockCapability}, Fabric {@code BlockApiLookup}, Forge capability) and owns the
 * shared {@code nerologistics:fluid} capability object that ducts and tanks register
 * against. Resolved through Core's {@link Services} ServiceLoader helper from the
 * {@code META-INF/services} entry shipped in each loader module.
 *
 * <p>Following the Core contract: Core froze the <em>energy</em> capability seam
 * ({@code EnergyLookup}); item and fluid endpoint discovery follow the same
 * ServiceLoader seam design but live inside NeroLogistics (see
 * {@code ../neroland-mc-ecosystem/nerologistics/PHASE-3-PLAN.md}).
 */
public interface IFluidLookup {

    IFluidLookup INSTANCE = Services.load(IFluidLookup.class);

    @Nullable
    NeroFluidStorage find(Level level, BlockPos pos, @Nullable Direction side);
}
