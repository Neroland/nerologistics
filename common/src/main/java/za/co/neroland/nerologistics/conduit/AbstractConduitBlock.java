package za.co.neroland.nerologistics.conduit;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Shared base for conduit blocks. A {@link BaseEntityBlock} that renders as a model and wires a
 * server-side ticker driving {@link AbstractConduitBlockEntity#serverTick}. Conduits are
 * non-directional (no facing state) and do not open Core's {@code INDUSTRIAL_POWER} gate — Nerotech
 * is that gate's canonical opener; conduits merely require it to be open before they transport.
 */
public abstract class AbstractConduitBlock extends BaseEntityBlock {

    protected AbstractConduitBlock(Properties properties) {
        super(properties);
    }

    /** The concrete conduit block-entity type, for the ticker. */
    protected abstract BlockEntityType<? extends AbstractConduitBlockEntity> conduitType();

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, conduitType(),
                (lvl, pos, st, be) -> AbstractConduitBlockEntity.serverTick(lvl, pos, st, be));
    }
}
