package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/** Create train cargo interface block — a passive inventory bridge (no ticker, no GUI). */
public class TrainCargoInterfaceBlock extends BaseEntityBlock {

    public static final MapCodec<TrainCargoInterfaceBlock> CODEC = simpleCodec(TrainCargoInterfaceBlock::new);

    public TrainCargoInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
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
