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

/** Wireless cargo terminal block — right-click to cycle its channel. */
public class WirelessCargoTerminalBlock extends BaseEntityBlock {

    public static final MapCodec<WirelessCargoTerminalBlock> CODEC = simpleCodec(WirelessCargoTerminalBlock::new);

    public WirelessCargoTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<WirelessCargoTerminalBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessCargoTerminalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof WirelessCargoTerminalBlockEntity terminal) {
            int channel = terminal.cycleChannel();
            serverPlayer.sendSystemMessage(
                    Component.translatable("block.nerologistics.wireless_cargo_terminal.channel", channel));
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
        return createTickerHelper(type, ModBlockEntities.WIRELESS_CARGO_TERMINAL.get(),
                (lvl, pos, st, be) -> WirelessCargoTerminalBlockEntity.serverTick(lvl, pos, st, be));
    }
}
