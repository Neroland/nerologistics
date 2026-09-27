package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.menu.MenuOpener;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Logistics Processor block — right-click opens the rule GUI (see
 * {@link LogisticsProcessorBlockEntity}). With {@code enableLogisticsProcessor=false} the block
 * stays placeable but refuses to open and rules stop evaluating, telling the player why (clean
 * degrade, same posture as the storage terminal).
 */
public class LogisticsProcessorBlock extends BaseEntityBlock {

    public static final MapCodec<LogisticsProcessorBlock> CODEC = BlockCodecs.simple(LogisticsProcessorBlock::new);

    public LogisticsProcessorBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<LogisticsProcessorBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsProcessorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LogisticsProcessorBlockEntity processor) {
            if (!NeroLogisticsConfig.enableLogisticsProcessor()) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("block.nerologistics.logistics_processor.disabled"));
            } else {
                MenuOpener.open(serverPlayer, processor);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Alarm output: the processor is a redstone source while any alarm-only rule's port condition holds. */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof LogisticsProcessorBlockEntity processor ? processor.signal() : 0;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.LOGISTICS_PROCESSOR.get(),
                (lvl, pos, st, be) -> LogisticsProcessorBlockEntity.serverTick(lvl, pos, st, be));
    }
}
