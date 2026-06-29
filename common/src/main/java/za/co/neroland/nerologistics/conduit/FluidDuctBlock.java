package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.registry.ModBlockEntities;

/** Fluid duct — routes fluids across its network. */
public class FluidDuctBlock extends AbstractConduitBlock {

    public static final MapCodec<FluidDuctBlock> CODEC = simpleCodec(FluidDuctBlock::new);

    public FluidDuctBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<FluidDuctBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidDuctBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractConduitBlockEntity> conduitType() {
        return ModBlockEntities.FLUID_DUCT.get();
    }
}
