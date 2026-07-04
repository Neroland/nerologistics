package za.co.neroland.nerologistics.conduit;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.network.TrainStationRegistry;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.ship.ShipmentManager;

/**
 * Train Station — the native, cheap, early-game bulk hauler. A 54-slot bulk buffer that a duct/hopper
 * loads or drains. Stations belong to a <b>line</b> (rename the station item in an anvil before placing);
 * a <b>load</b> station periodically hauls its whole buffer, in bulk, to a same-line <b>unload</b> station
 * in the same dimension, on a distance-based transit timer — slower than ducts, but big and free (no
 * energy). With no line name a load station ships to the nearest unload station (Auto).
 *
 * <p>The haul reuses {@link ShipmentManager}'s in-transit queue (already ticked every server tick), so the
 * payload sits in the durable shipment store for the transit time and is deposited into the unload station
 * on arrival — an in-flight haul survives a server restart. Sneak-click toggles load/unload. Right-click
 * opens a vanilla double-chest GUI (no custom menu/screen).
 *
 * <p><b>Visual follow-up:</b> physical rail blocks, a visible train entity riding them, and the animated
 * 3D models from MODELS.md are a separate Stage-12 art pass. <b>Create</b> trains remain supported via the
 * existing {@link TrainCargoInterfaceBlockEntity}.
 */
public class TrainStationBlockEntity extends AbstractTerminalBlockEntity implements MenuProvider {

    public enum Mode {
        LOAD,
        UNLOAD
    }

    public static final int SLOTS = 54;

    private Mode mode = Mode.LOAD;
    private String line = "";
    private boolean joined;

    public TrainStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAIN_STATION.get(), pos, state, SLOTS, 0, 0);
    }

    public Mode mode() {
        return this.mode;
    }

    public Mode cycleMode() {
        this.mode = this.mode == Mode.LOAD ? Mode.UNLOAD : Mode.LOAD;
        setChanged();
        return this.mode;
    }

    public String line() {
        return this.line;
    }

    public void setLine(String line) {
        this.line = line == null ? "" : line;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return this.line.isEmpty()
                ? Component.translatable("block.nerologistics.train_station")
                : Component.literal(this.line);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return ChestMenu.sixRows(id, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Mode", this.mode.ordinal());
        output.putString("Line", this.line);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int ordinal = input.getIntOr("Mode", Mode.LOAD.ordinal());
        Mode[] modes = Mode.values();
        this.mode = ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : Mode.LOAD;
        this.line = input.getStringOr("Line", "");
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide()) {
            TrainStationRegistry.onRemoved(this.level, this.worldPosition);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TrainStationBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!be.joined) {
            TrainStationRegistry.onPlaced(level, pos);
            be.joined = true;
        }
        if (!NeroLogisticsConfig.enableTrains() || be.mode != Mode.LOAD) {
            return;
        }
        if (level.getGameTime() % NeroLogisticsConfig.trainStationIntervalTicks() != 0L) {
            return;
        }
        be.haul(serverLevel, pos);
    }

    private void haul(ServerLevel level, BlockPos pos) {
        if (this.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null || ShipmentManager.atCapacity(server)) {
            return;
        }
        BlockPos dest = findUnloadStation(level, pos);
        if (dest == null) {
            return;
        }
        List<ItemStack> payload = collectPayload();
        if (payload.isEmpty()) {
            return;
        }
        double distance = Math.sqrt(pos.distSqr(dest));
        int transit = (int) Math.max(NeroLogisticsConfig.trainMinTransitTicks(),
                Math.min((long) (distance * NeroLogisticsConfig.trainTicksPerBlock()), 1_728_000L));
        ShipmentManager.ship(server, payload, level.dimension(), pos, level.dimension(), dest, transit);
        setChanged();
    }

    /** Take up to the per-trip bulk limit of non-empty stacks out of the buffer. */
    private List<ItemStack> collectPayload() {
        int limit = NeroLogisticsConfig.trainBulkPerTrip();
        List<ItemStack> payload = new ArrayList<>();
        for (int i = 0; i < getContainerSize() && payload.size() < limit; i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                payload.add(stack.copy());
                setItem(i, ItemStack.EMPTY);
            }
        }
        return payload;
    }

    @Nullable
    private BlockPos findUnloadStation(ServerLevel level, BlockPos pos) {
        boolean named = !this.line.isEmpty();
        long rangeSq = (long) NeroLogisticsConfig.trainMaxRange() * NeroLogisticsConfig.trainMaxRange();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : TrainStationRegistry.membersOf(level)) {
            if (candidate.equals(pos)) {
                continue;
            }
            double distSq = pos.distSqr(candidate);
            if (distSq > rangeSq || distSq >= bestDist) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(candidate);
            if (!(be instanceof TrainStationBlockEntity station) || station.mode != Mode.UNLOAD) {
                continue;
            }
            if (named && !this.line.equalsIgnoreCase(station.line)) {
                continue;
            }
            best = candidate;
            bestDist = distSq;
        }
        return best;
    }
}
