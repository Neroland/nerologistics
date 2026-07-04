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
 * Network Controller block — the single brain of a NeroLogistics network. Right-click reports its
 * managed status, module count and throughput multiplier (and, on conflict, the hint to bridge
 * separate networks with drones instead of wiring two controllers together).
 */
public class NetworkControllerBlock extends BaseEntityBlock {

    public static final MapCodec<NetworkControllerBlock> CODEC = simpleCodec(NetworkControllerBlock::new);

    public NetworkControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<NetworkControllerBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkControllerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller) {
            NetworkControllerBlockEntity.Status status = controller.status();
            int percent = (int) Math.round(controller.capacityMultiplier() * 100.0);
            serverPlayer.sendSystemMessage(Component.translatable(
                    "block.nerologistics.network_controller.status",
                    Component.translatable("block.nerologistics.network_controller.status." + status.name().toLowerCase()),
                    controller.moduleCount(), percent));
            if (status == NetworkControllerBlockEntity.Status.CONFLICT) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("block.nerologistics.network_controller.conflict_hint"));
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
        return createTickerHelper(type, ModBlockEntities.NETWORK_CONTROLLER.get(),
                (lvl, pos, st, be) -> NetworkControllerBlockEntity.serverTick(lvl, pos, st, be));
    }
}
