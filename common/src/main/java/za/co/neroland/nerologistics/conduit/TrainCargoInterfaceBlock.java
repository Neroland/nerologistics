package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/** Create train cargo interface block — a passive inventory bridge (no ticker, no GUI). */
public class TrainCargoInterfaceBlock extends OrientedDeviceBlock {

    public static final MapCodec<TrainCargoInterfaceBlock> CODEC = BlockCodecs.simple(TrainCargoInterfaceBlock::new);

    public TrainCargoInterfaceBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<TrainCargoInterfaceBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrainCargoInterfaceBlockEntity(pos, state);
    }
}
