package za.co.neroland.nerologistics.conduit;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.RelativeFace;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SideMode;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;

/**
 * Shared base for every conduit block-entity. Holds the per-face {@link SideConfig} for the conduit's
 * single {@link NetworkMedium} channel, joins/leaves its {@link NetworkManager} network, and is driven
 * once per tick by the block ticker (which runs the owning network's transport, idempotent per tick).
 *
 * <p>Conduits buffer nothing and store no inventory; the side config's slot groups are unused — face
 * {@link SideMode}s govern only how the conduit interacts with the <em>external</em> block on that face
 * (INPUT = pull from it into the network, OUTPUT = push to it, IO = both, DISABLED = ignore). Conduit-to
 * -conduit connectivity is graph membership, independent of face mode. Faces default to {@link
 * SideMode#IO} so a freshly-placed line balances adjacent inventories before the player refines it.
 */
public abstract class AbstractConduitBlockEntity extends BlockEntity {

    /** Conduits are non-directional; relative faces are resolved against a fixed reference facing. */
    private static final Direction REFERENCE_FACING = Direction.NORTH;

    protected final SideConfig sideConfig;
    private boolean joined;

    protected AbstractConduitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.sideConfig = defaultSideConfig(medium().channel());
    }

    /** The primary medium this conduit carries (also the channel its face config is stored under). */
    public abstract NetworkMedium medium();

    /**
     * Every medium this conduit carries. Single-medium conduits (item/fluid/energy ducts) return just
     * their {@link #medium()}; the universal duct overrides this to join several per-medium networks at
     * once (e.g. {@code ITEM} + {@code FLUID}). Face modes are shared across a conduit's media — they are
     * read through {@link #faceMode(Direction)} on the primary channel.
     */
    public Set<NetworkMedium> media() {
        return Set.of(medium());
    }

    private static SideConfig defaultSideConfig(Channel channel) {
        SideConfig config = SideConfig.builder()
                .channel(channel)
                .defaultPreset(SidePreset.ALL_DISABLED)
                .build();
        for (RelativeFace face : RelativeFace.values()) {
            config.setMode(channel, face, SideMode.IO);
        }
        return config;
    }

    /** The configured {@link SideMode} of the absolute {@code side} face for this conduit's channel. */
    public SideMode faceMode(Direction side) {
        return this.sideConfig.modeAbsolute(medium().channel(), REFERENCE_FACING, side);
    }

    /** Set an absolute face's mode; invalidates the network's cached endpoints on change. */
    public boolean setFaceMode(Direction side, SideMode mode) {
        boolean changed = this.sideConfig.setModeAbsolute(medium().channel(), REFERENCE_FACING, side, mode);
        if (changed) {
            setChanged();
            if (this.level != null && !this.level.isClientSide()) {
                for (NetworkMedium m : media()) {
                    NetworkManager.invalidateAt(this.level, m, this.worldPosition);
                }
            }
        }
        return changed;
    }

    /** Whether {@code stack} may pass this conduit's {@code face}. Overridden by item ducts. */
    public boolean itemPasses(Direction face, ItemStack stack) {
        return true;
    }

    /** Whether {@code fluid} may pass this conduit's {@code face}. Overridden by fluid ducts. */
    public boolean fluidPasses(Direction face, Fluid fluid) {
        return true;
    }

    public SideConfig sideConfig() {
        return this.sideConfig;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.sideConfig.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.sideConfig.load(input);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide()) {
            for (NetworkMedium m : media()) {
                NetworkManager.onRemoved(this.level, this.worldPosition, m);
            }
        }
    }

    /** Server ticker target wired by the block: lazily joins each medium's network, then drives transport. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractConduitBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }
        if (!be.joined) {
            for (NetworkMedium m : be.media()) {
                NetworkManager.onPlaced(level, pos, m);
            }
            be.joined = true;
        }
        if (level instanceof ServerLevel serverLevel) {
            for (NetworkMedium m : be.media()) {
                NetworkManager.tick(serverLevel, m, pos);
            }
        }
    }
}
