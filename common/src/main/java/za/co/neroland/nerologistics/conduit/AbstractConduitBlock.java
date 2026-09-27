package za.co.neroland.nerologistics.conduit;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.platform.EnergyLookup;
import za.co.neroland.nerolandcore.platform.FluidLookup;
import za.co.neroland.nerolandcore.sideconfig.SideMode;

import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.storage.StorageNode;

/**
 * Shared base for conduit blocks. A {@link BaseEntityBlock} that wires a server-side ticker driving
 * {@link AbstractConduitBlockEntity#serverTick}. Conduits are non-directional (no facing state).
 *
 * <p><b>Look (cosmetic only).</b> Conduits render like Nerospace's Universal Pipe: a translucent tube with
 * a glowing core line, plus one arm per connected side, drawn by a multipart model driven by six boolean
 * connection properties that also shape the hitbox. The properties are <em>derived</em> state for the
 * renderer — transport never reads them: network membership and endpoints are still decided by
 * {@code NetworkManager} / {@code ConduitNetwork} exactly as before, so throughput is unchanged.</p>
 *
 * <p>An arm is shown towards a conduit that shares a medium, and — unless that face is {@code DISABLED} —
 * towards anything the conduit can actually move its medium to or from: an item {@link Container} or
 * drive bay (items), a Core fluid storage or drive bay (fluids), a Core energy storage (energy), or a
 * network controller. Connections are computed on the server only (face modes live there) and pushed to
 * clients as ordinary block-state updates: on placement, on a neighbour change, when a face mode changes,
 * and on a slow staggered safety refresh for storages that appear without a block update.</p>
 */
public abstract class AbstractConduitBlock extends BaseEntityBlock {

    /** Connection property per direction, ordered by {@link Direction#get3DDataValue()}. */
    public static final BooleanProperty[] CONNECTIONS = {
            BlockStateProperties.DOWN, BlockStateProperties.UP, BlockStateProperties.NORTH,
            BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.EAST};

    /** Hitbox per 6-bit connection mask: an 8×8 core plus 8×8 arms, like the Universal Pipe. */
    private static final VoxelShape[] SHAPES = buildShapes();

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    protected AbstractConduitBlock(Properties properties) {
        super(properties);
        BlockState base = this.stateDefinition.any();
        for (BooleanProperty prop : CONNECTIONS) {
            base = base.setValue(prop, false);
        }
        registerDefaultState(base);
    }

    /** The concrete conduit block-entity type, for the ticker. */
    protected abstract BlockEntityType<? extends AbstractConduitBlockEntity> conduitType();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTIONS);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[connectionMask(state)];
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape core = Block.box(4, 4, 4, 12, 12, 12);
        VoxelShape[] arms = {
                Block.box(4, 0, 4, 12, 4, 12),   // down
                Block.box(4, 12, 4, 12, 16, 12), // up
                Block.box(4, 4, 0, 12, 12, 4),   // north
                Block.box(4, 4, 12, 12, 12, 16), // south
                Block.box(0, 4, 4, 4, 12, 12),   // west
                Block.box(12, 4, 4, 16, 12, 12), // east
        };
        VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (int d = 0; d < 6; d++) {
                if ((mask & (1 << d)) != 0) {
                    shape = Shapes.or(shape, arms[d]);
                }
            }
            shapes[mask] = shape;
        }
        return shapes;
    }

    private static int connectionMask(BlockState state) {
        int mask = 0;
        for (int d = 0; d < 6; d++) {
            if (state.getValue(CONNECTIONS[d])) {
                mask |= 1 << d;
            }
        }
        return mask;
    }

    // --- Connections (cosmetic) ------------------------------------------------------

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Server only: face modes and storage lookups live there; clients get the result as a state update.
        Level level = context.getLevel();
        return level.isClientSide() ? defaultBlockState()
                : withConnections(defaultBlockState(), level, context.getClickedPos(), null);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        refreshConnections(level, pos);
    }

    /** Recompute this conduit's arms and push the state to clients if they changed. Server side only. */
    public static void refreshConnections(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractConduitBlock)) {
            return;
        }
        AbstractConduitBlockEntity self = level.getBlockEntity(pos) instanceof AbstractConduitBlockEntity be ? be : null;
        BlockState connected = withConnections(state, level, pos, self);
        if (connected != state) {
            // UPDATE_CLIENTS only: the arms are cosmetic, so no neighbour-update cascade is needed.
            level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState withConnections(BlockState state, Level level, BlockPos pos,
            @Nullable AbstractConduitBlockEntity self) {
        Set<NetworkMedium> media = self != null ? self.media()
                : state.getBlock() instanceof AbstractConduitBlock conduit ? conduit.carriedMedia() : Set.of();
        for (Direction dir : Direction.values()) {
            SideMode mode = self != null ? self.faceMode(dir) : SideMode.IO; // new conduits default to IO
            state = state.setValue(CONNECTIONS[dir.get3DDataValue()], connects(level, pos, dir, media, mode));
        }
        return state;
    }

    /** The media this conduit carries (used at placement, before its block entity exists). */
    protected abstract Set<NetworkMedium> carriedMedia();

    private static boolean connects(Level level, BlockPos pos, Direction dir, Set<NetworkMedium> media,
            SideMode mode) {
        BlockPos np = pos.relative(dir);
        BlockEntity neighbour = level.getBlockEntity(np);
        if (neighbour instanceof AbstractConduitBlockEntity other) {
            for (NetworkMedium m : other.media()) {
                if (media.contains(m)) {
                    return true; // same-medium conduits join one network
                }
            }
            return false;
        }
        if (mode == SideMode.DISABLED || neighbour == null) {
            return false;
        }
        if (neighbour instanceof NetworkControllerBlockEntity) {
            return true;
        }
        Direction side = dir.getOpposite();
        for (NetworkMedium m : media) {
            boolean ok = switch (m) {
                case ITEM -> neighbour instanceof Container || neighbour instanceof StorageNode;
                case FLUID -> neighbour instanceof StorageNode || FluidLookup.INSTANCE.find(level, np, side) != null;
                case ENERGY -> EnergyLookup.INSTANCE.find(level, np, side) != null;
            };
            if (ok) {
                return true;
            }
        }
        return false;
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
