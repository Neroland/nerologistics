package za.co.neroland.nerologistics.storage;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.menu.MenuOpener;

/**
 * Storage Terminal block — right-click opens the network browse/insert/extract GUI (see
 * {@link StorageTerminalBlockEntity}). With {@code enableStorageTerminal=false} the block stays
 * placeable but refuses to open, telling the player why (clean degrade).
 */
public class StorageTerminalBlock extends BaseEntityBlock {

    public static final MapCodec<StorageTerminalBlock> CODEC = simpleCodec(StorageTerminalBlock::new);

    public StorageTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<StorageTerminalBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageTerminalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal) {
            if (!NeroLogisticsConfig.enableStorageTerminal()
                    || !NeroLogisticsConfig.enableStorageNetwork()) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("block.nerologistics.storage_terminal.disabled"));
            } else {
                MenuOpener.open(serverPlayer, terminal);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
