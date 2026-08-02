package za.co.neroland.nerologistics.storage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.menu.MenuOpener;

/**
 * Drive Bay block — right-click opens the six-bay cell GUI. Passive (no ticker); the network
 * storage index reads the {@link DriveBayBlockEntity} through the {@code StorageNode} contract.
 * Comparator output is the average cell fill (0–15).
 */
public class DriveBayBlock extends BaseEntityBlock {

    public static final MapCodec<DriveBayBlock> CODEC = simpleCodec(DriveBayBlock::new);

    public DriveBayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DriveBayBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DriveBayBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DriveBayBlockEntity drive) {
            MenuOpener.open(serverPlayer, drive);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof DriveBayBlockEntity drive
                ? Mth.clamp((int) Math.round(drive.fillFraction() * 15.0), 0, 15)
                : 0;
    }
}
