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
 * Drone Port block — right-click opens the GUI (drones, upgrades, cargo); <b>sneak</b>-right-click
 * toggles import/export. An anvil-renamed port item names the port on placement (used for routing). A
 * server ticker drives export dispatch.
 */
public class DronePortBlock extends BaseEntityBlock {

    public static final MapCodec<DronePortBlock> CODEC = simpleCodec(DronePortBlock::new);

    public DronePortBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DronePortBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DronePortBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null && level.getBlockEntity(pos) instanceof DronePortBlockEntity port) {
            port.setPortName(custom.getString());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DronePortBlockEntity port) {
            if (player.isShiftKeyDown()) {
                DronePortBlockEntity.Mode mode = port.cycleMode();
                serverPlayer.sendSystemMessage(Component.translatable(
                        "block.nerologistics.drone_port.mode",
                        Component.translatable("block.nerologistics.drone_port.mode." + mode.name().toLowerCase())));
            } else {
                serverPlayer.openMenu(port);
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
        return createTickerHelper(type, ModBlockEntities.DRONE_PORT.get(),
                (lvl, pos, st, be) -> DronePortBlockEntity.serverTick(lvl, pos, st, be));
    }
}
