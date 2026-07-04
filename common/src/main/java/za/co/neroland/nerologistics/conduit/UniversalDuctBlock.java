package za.co.neroland.nerologistics.conduit;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Universal duct — the Stage-8 default conduit, carrying items and fluids on one network. Bare-hand
 * right-click opens the item whitelist GUI; the Configurator sets per-face modes (shared across both
 * media).
 */
public class UniversalDuctBlock extends AbstractConduitBlock {

    public static final MapCodec<UniversalDuctBlock> CODEC = simpleCodec(UniversalDuctBlock::new);

    public UniversalDuctBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<UniversalDuctBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UniversalDuctBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MenuProvider provider) {
            serverPlayer.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockEntityType<? extends AbstractConduitBlockEntity> conduitType() {
        return ModBlockEntities.UNIVERSAL_DUCT.get();
    }
}
