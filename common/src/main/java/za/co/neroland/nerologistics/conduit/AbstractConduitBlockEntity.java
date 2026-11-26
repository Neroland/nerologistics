package za.co.neroland.nerologistics.conduit;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerPlayer;
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
import za.co.neroland.nerolandcore.sideconfig.ConfiguratorTool;
import za.co.neroland.nerologistics.menu.FilterMenu;
import za.co.neroland.nerologistics.filter.FluidFilter;
import za.co.neroland.nerologistics.menu.MenuOpener;
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
public abstract class AbstractConduitBlockEntity extends BlockEntity implements MenuProvider, ConfiguratorTool.Target {

    /** Ticks between a conduit's cosmetic arm re-checks (staggered by position so a line never spikes). */
    private static final int CONNECTION_REFRESH_TICKS = 100;

    /** Conduits are non-directional; relative faces are resolved against a fixed reference facing. */
    private static final Direction REFERENCE_FACING = Direction.NORTH;

    protected final SideConfig sideConfig;
    private boolean joined;
    private final SimpleContainer fluidSamples = new SimpleContainer(6);

    public double energyRate() {
        var level = this.level;
        if (level == null || !media().contains(NetworkMedium.ENERGY)) {
            return 0;
        }
        var network = NetworkManager.networkAt(level, NetworkMedium.ENERGY, this.worldPosition);
        return network == null ? 0 : network.energyRate(level.getGameTime());
    }

    public SimpleContainer fluidSamples() {
        return this.fluidSamples;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FilterMenu(id, inventory, new SimpleContainer(FilterMenu.ITEM_FILTER_SLOTS), this);
    }

    @Override
    public InteractionResult configure(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (context.isSecondaryUseActive()) {
                MenuOpener.open(player, this);
            } else {
                Direction face = context.getClickedFace();
                SideMode mode = cycleFaceMode(face);
                player.sendSystemMessage(Component.translatable("item.nerologistics.configurator.set",
                        face.getName(), mode.lowerName()), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    public SideMode cycleFaceMode(Direction face) {
        SideMode mode = switch (faceMode(face)) {
            case DISABLED -> SideMode.INPUT;
            case INPUT -> SideMode.OUTPUT;
            case OUTPUT -> SideMode.IO;
            default -> SideMode.DISABLED;
        };
        setFaceMode(face, mode);
        return mode;
    }

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
                // A DISABLED face hides its arm; re-enabling it shows the arm again (cosmetic only).
                AbstractConduitBlock.refreshConnections(this.level, this.worldPosition);
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
        return FluidFilter.matchesSample(this.fluidSamples.getItem(face.get3DDataValue()), fluid);
    }

    public SideConfig sideConfig() {
        return this.sideConfig;
    }

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        this.sideConfig.save(output);
        if (media().contains(NetworkMedium.FLUID)) {
            for (Direction face : Direction.values()) {
                int slot = face.get3DDataValue();
                output.store("FluidSample" + slot, ItemStack.OPTIONAL_CODEC, this.fluidSamples.getItem(slot));
            }
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        this.sideConfig.load(input);
        if (media().contains(NetworkMedium.FLUID)) {
            for (Direction face : Direction.values()) {
                int slot = face.get3DDataValue();
                this.fluidSamples.setItem(slot, input.read("FluidSample" + slot, ItemStack.OPTIONAL_CODEC)
                        .orElse(ItemStack.EMPTY));
            }
        }
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
            AbstractConduitBlock.refreshConnections(level, pos); // arms for worlds saved before 0.4
        } else if (level.getGameTime() % CONNECTION_REFRESH_TICKS == Math.floorMod(pos.hashCode(), CONNECTION_REFRESH_TICKS)) {
            // Slow, position-staggered safety net for storages that appear without a block update (e.g.
            // another mod's block entity finishing its setup). Cosmetic only; neighbour changes and
            // face-mode edits refresh immediately.
            AbstractConduitBlock.refreshConnections(level, pos);
        }
        if (level instanceof ServerLevel serverLevel) {
            for (NetworkMedium m : be.media()) {
                NetworkManager.tick(serverLevel, m, pos);
            }
        }
    }
}
