package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Energy cable block-entity. Routes Core NE between adjacent {@code NeroEnergyStorage}s discovered via
 * Core's {@code EnergyLookup}; carries no filter and no buffer.
 */
public class EnergyCableBlockEntity extends AbstractConduitBlockEntity {

    public EnergyCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_CABLE.get(), pos, state);
    }

    @Override
    public NetworkMedium medium() {
        return NetworkMedium.ENERGY;
    }
}
