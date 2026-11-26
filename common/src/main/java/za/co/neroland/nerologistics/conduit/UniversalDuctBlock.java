package za.co.neroland.nerologistics.conduit;

import java.util.Set;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Universal duct — the Stage-8 default conduit, carrying items and fluids on one network. Bare-hand
 * right-click opens the item whitelist GUI; the Configurator sets per-face modes (shared across both
 * media).
 */
public class UniversalDuctBlock extends AbstractConduitBlock {

    public static final MapCodec<UniversalDuctBlock> CODEC = BlockCodecs.simple(UniversalDuctBlock::new);

    public UniversalDuctBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<UniversalDuctBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UniversalDuctBlockEntity(pos, state);
    }

    @Override
    protected Set<NetworkMedium> carriedMedia() {
        return Set.of(NetworkMedium.ITEM, NetworkMedium.FLUID);
    }

    @Override
    protected BlockEntityType<? extends AbstractConduitBlockEntity> conduitType() {
        return ModBlockEntities.UNIVERSAL_DUCT.get();
    }
}
