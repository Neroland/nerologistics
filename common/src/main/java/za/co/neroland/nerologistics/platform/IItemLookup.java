package za.co.neroland.nerologistics.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.platform.Services;
import za.co.neroland.nerologistics.transport.ItemPort;

/**
 * Query side of the item seam: find the {@link ItemPort} exposed by the block at
 * {@code pos} on {@code side}. The item analogue of Core's {@code platform.EnergyLookup}
 * — endpoint discovery for the routing graph. Each loader implements it over its own
 * lookup mechanism (NeoForge {@code BlockCapability}, Fabric {@code BlockApiLookup},
 * Forge capability) and owns the shared {@code nerologistics:item} capability object
 * that ducts and machines register against. Resolved through Core's {@link Services}
 * ServiceLoader helper from the {@code META-INF/services} entry shipped in each loader
 * module.
 *
 * <p>Following the Core contract: Core froze the <em>energy</em> capability seam
 * ({@code EnergyLookup}); item and fluid endpoint discovery follow the same
 * ServiceLoader seam design but live inside NeroLogistics (see
 * {@code ../neroland-mc-ecosystem/nerologistics/PHASE-3-PLAN.md}).
 */
public interface IItemLookup {

    IItemLookup INSTANCE = Services.load(IItemLookup.class);

    @Nullable
    ItemPort find(Level level, BlockPos pos, @Nullable Direction side);
}
