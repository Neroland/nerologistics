package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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

import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Train Station block — right-click opens its bulk chest GUI; <b>sneak</b>-right-click toggles load/unload.
 * An anvil-renamed station item sets the station's line on placement (used for routing). A server ticker
 * drives load-station hauling.
 */
public class TrainStationBlock extends BaseEntityBlock {

    public static final MapCodec<TrainStationBlock> CODEC = simpleCodec(TrainStationBlock::new);

    public TrainStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<TrainStationBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrainStationBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null && level.getBlockEntity(pos) instanceof TrainStationBlockEntity station) {
            station.setLine(custom.getString());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TrainStationBlockEntity station) {
            if (player.isShiftKeyDown()) {
                TrainStationBlockEntity.Mode mode = station.cycleMode();
                serverPlayer.sendSystemMessage(Component.translatable(
                        "block.nerologistics.train_station.mode",
                        Component.translatable("block.nerologistics.train_station.mode." + mode.name().toLowerCase())));
            } else {
                serverPlayer.openMenu(station);
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
        return createTickerHelper(type, ModBlockEntities.TRAIN_STATION.get(),
                (lvl, pos, st, be) -> TrainStationBlockEntity.serverTick(lvl, pos, st, be));
    }
}
