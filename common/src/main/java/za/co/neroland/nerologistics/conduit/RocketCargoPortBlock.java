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

/** Rocket cargo port block — right-click cycles destination, sneak-right-click cycles channel. */
public class RocketCargoPortBlock extends BaseEntityBlock {

    public static final MapCodec<RocketCargoPortBlock> CODEC = simpleCodec(RocketCargoPortBlock::new);

    public RocketCargoPortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RocketCargoPortBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RocketCargoPortBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getServer() != null
                && level.getBlockEntity(pos) instanceof RocketCargoPortBlockEntity port) {
            if (player.isShiftKeyDown()) {
                int channel = port.cycleChannel();
                serverPlayer.sendSystemMessage(
                        Component.translatable("block.nerologistics.rocket_cargo_port.channel", channel));
            } else {
                String dest = port.cycleDestination(level.getServer());
                serverPlayer.sendSystemMessage(
                        Component.translatable("block.nerologistics.rocket_cargo_port.destination", dest));
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
        return createTickerHelper(type, ModBlockEntities.ROCKET_CARGO_PORT.get(),
                (lvl, pos, st, be) -> RocketCargoPortBlockEntity.serverTick(lvl, pos, st, be));
    }
}
