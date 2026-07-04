package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Buffer block — right-click opens the target GUI; <b>sneak</b>-right-click toggles between keep-stocked
 * and passive mode. A server ticker drives the keep-stocked leveling.
 */
public class BufferBlock extends BaseEntityBlock {

    public static final MapCodec<BufferBlock> CODEC = simpleCodec(BufferBlock::new);

    public BufferBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<BufferBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BufferBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof BufferBlockEntity buffer) {
            if (player.isShiftKeyDown()) {
                BufferBlockEntity.Mode mode = buffer.cycleMode();
                serverPlayer.sendSystemMessage(Component.translatable(
                        "block.nerologistics.buffer.mode",
                        Component.translatable("block.nerologistics.buffer.mode." + mode.name().toLowerCase())));
            } else {
                serverPlayer.openMenu(buffer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.BUFFER.get(),
                (lvl, pos, st, be) -> BufferBlockEntity.serverTick(lvl, pos, st, be));
    }
}
