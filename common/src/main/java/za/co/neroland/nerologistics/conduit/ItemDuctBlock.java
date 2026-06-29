package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.registry.ModBlockEntities;

/** Item duct — routes items across its network. */
public class ItemDuctBlock extends AbstractConduitBlock {

    public static final MapCodec<ItemDuctBlock> CODEC = simpleCodec(ItemDuctBlock::new);

    public ItemDuctBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ItemDuctBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemDuctBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractConduitBlockEntity> conduitType() {
        return ModBlockEntities.ITEM_DUCT.get();
    }
}
