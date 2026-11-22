package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import za.co.neroland.nerologistics.progression.OperatorCredit;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/** Drone hub block — right-click to cycle the delivery channel. */
public class DroneHubBlock extends BaseEntityBlock {

    public static final MapCodec<DroneHubBlock> CODEC = BlockCodecs.simple(DroneHubBlock::new);

    public DroneHubBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<DroneHubBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DroneHubBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Transient, in-memory only: who to credit when this block first does its job (see OperatorCredit).
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof DroneHubBlockEntity be) {
            OperatorCredit.note(be, player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DroneHubBlockEntity hub) {
            OperatorCredit.note(hub, serverPlayer.getUUID());
            int channel = hub.cycleChannel();
            serverPlayer.sendSystemMessage(
                    Component.translatable("block.nerologistics.drone_hub.channel", channel));
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
        return createTickerHelper(type, ModBlockEntities.DRONE_HUB.get(),
                (lvl, pos, st, be) -> DroneHubBlockEntity.serverTick(lvl, pos, st, be));
    }
}
