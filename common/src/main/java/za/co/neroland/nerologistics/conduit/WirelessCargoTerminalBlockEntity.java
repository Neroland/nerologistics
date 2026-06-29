package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerologistics.network.WirelessRegistry;
import za.co.neroland.nerologistics.registry.ModBlockEntities;

/**
 * Wireless cargo terminal: a buffered virtual endpoint keyed to a channel. Ducts/hoppers fill its
 * buffer to send and drain it to receive; {@link WirelessRegistry} links same-channel terminals within
 * range, charging this terminal's energy buffer per item sent. The channel is cycled by right-clicking.
 */
public class WirelessCargoTerminalBlockEntity extends AbstractTerminalBlockEntity {

    public static final int BUFFER_SIZE = 9;
    public static final int ENERGY_CAPACITY = 100_000;
    public static final int ENERGY_MAX_IO = 2_000;
    public static final int CHANNELS = 16;

    private int channel;
    private boolean joined;

    public WirelessCargoTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIRELESS_CARGO_TERMINAL.get(), pos, state, BUFFER_SIZE, ENERGY_CAPACITY, ENERGY_MAX_IO);
    }

    public int channel() {
        return this.channel;
    }

    /** Cycle to the next channel, re-keying membership in the wireless registry. */
    public int cycleChannel() {
        int old = this.channel;
        this.channel = (this.channel + 1) % CHANNELS;
        if (this.level != null && !this.level.isClientSide() && this.joined) {
            WirelessRegistry.rechannel(this.level, this.worldPosition, old, this.channel);
        }
        setChanged();
        return this.channel;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Channel", this.channel);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.channel = input.getIntOr("Channel", 0);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide()) {
            WirelessRegistry.onRemoved(this.level, this.worldPosition, this.channel);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            WirelessCargoTerminalBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }
        if (!be.joined) {
            WirelessRegistry.onPlaced(level, pos, be.channel);
            be.joined = true;
        }
        if (level instanceof ServerLevel serverLevel) {
            WirelessRegistry.tick(serverLevel, be.channel);
        }
    }
}
