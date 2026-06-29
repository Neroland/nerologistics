package za.co.neroland.nerologistics.conduit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.progression.CoreGates;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.ship.RouteDestination;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShipmentManager;

/**
 * Rocket cargo port: buffers cargo, draws energy from cables, and on an interval launches a
 * cross-dimension shipment of its non-fuel cargo to a same-channel port in the selected destination
 * dimension. Deliberately slow and energy-hungry; consumes rocket fuel <em>by tag</em>
 * ({@code nerologistics:rocket_fuel}) rather than any Nerospace class; gated behind
 * {@link CoreGates#REACHED_ORBIT}. Destinations come from the {@link RouteProviders} seam (a stub until
 * a Nerospace-backed provider is registered). Right-click cycles destination; sneak-right-click cycles
 * channel.
 */
public class RocketCargoPortBlockEntity extends AbstractTerminalBlockEntity {

    public static final int BUFFER_SIZE = 10;
    public static final int ENERGY_CAPACITY = 500_000;
    public static final int ENERGY_MAX_IO = 8_000;

    /** Rocket fuel is matched by tag so any mod's fuel (incl. Nerospace's) can power a launch. */
    public static final TagKey<Item> ROCKET_FUEL = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "rocket_fuel"));

    private int channel;
    private int destIndex;
    private boolean joined;
    /** Placing player's UUID — stored ONLY when per-player attribution is opted in (POPIA/GDPR). */
    @org.jetbrains.annotations.Nullable
    private UUID owner;

    public RocketCargoPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROCKET_CARGO_PORT.get(), pos, state, BUFFER_SIZE, ENERGY_CAPACITY, ENERGY_MAX_IO);
    }

    public int channel() {
        return this.channel;
    }

    /** Record the placing player for opt-in attribution. Only call when attribution is enabled. */
    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public int cycleChannel() {
        int old = this.channel;
        this.channel = (this.channel + 1) % WirelessCargoTerminalBlockEntity.CHANNELS;
        if (this.level != null && !this.level.isClientSide() && this.joined) {
            ShipmentManager.rechannelPort(this.level, this.worldPosition, old, this.channel);
        }
        setChanged();
        return this.channel;
    }

    /** Cycle the destination; returns the new destination's name (or "none"). */
    public String cycleDestination(MinecraftServer server) {
        List<RouteDestination> dests = RouteProviders.get().destinations(server);
        if (dests.isEmpty()) {
            return "none";
        }
        this.destIndex = Math.floorMod(this.destIndex + 1, dests.size());
        setChanged();
        return dests.get(this.destIndex).name();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Channel", this.channel);
        output.putInt("DestIndex", this.destIndex);
        output.putLong("OwnerMost", this.owner == null ? 0L : this.owner.getMostSignificantBits());
        output.putLong("OwnerLeast", this.owner == null ? 0L : this.owner.getLeastSignificantBits());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.channel = input.getIntOr("Channel", 0);
        this.destIndex = input.getIntOr("DestIndex", 0);
        long most = input.getLongOr("OwnerMost", 0L);
        long least = input.getLongOr("OwnerLeast", 0L);
        this.owner = (most == 0L && least == 0L) ? null : new UUID(most, least);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide()) {
            ShipmentManager.unregisterPort(this.level, this.worldPosition, this.channel);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RocketCargoPortBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!be.joined) {
            ShipmentManager.registerPort(level, pos, be.channel);
            be.joined = true;
        }
        if (level.getGameTime() % NeroLogisticsConfig.shipIntervalTicks() == 0L) {
            be.tryShip(serverLevel, pos);
        }
    }

    private void tryShip(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null || !ProgressionGates.isServerOpen(server, CoreGates.REACHED_ORBIT)) {
            return;
        }
        List<RouteDestination> dests = RouteProviders.get().destinations(server);
        if (dests.isEmpty()) {
            return;
        }
        RouteDestination dest = dests.get(Math.floorMod(this.destIndex, dests.size()));
        BlockPos exclude = dest.dimension().equals(level.dimension()) ? pos : null;
        BlockPos target = ShipmentManager.findPort(dest.dimension(), this.channel, exclude);
        if (target == null) {
            return;
        }
        int stacks = 0;
        for (int slot = 0; slot < this.buffer.getContainerSize(); slot++) {
            ItemStack stack = this.buffer.getItem(slot);
            if (!stack.isEmpty() && !stack.is(ROCKET_FUEL)) {
                stacks++;
            }
        }
        if (stacks == 0) {
            return;
        }
        int energyCost = stacks * NeroLogisticsConfig.shipEnergyPerStack();
        if (this.energy.getAmount() < energyCost) {
            return;
        }
        int fuelNeed = NeroLogisticsConfig.shipFuelPerLaunch();
        int fuelSlot = fuelNeed > 0 ? findFuel(fuelNeed) : -1;
        if (fuelNeed > 0 && fuelSlot < 0) {
            return;
        }
        // Commit: charge energy + fuel, then collect the cargo and launch it.
        this.energy.consume(energyCost);
        if (fuelNeed > 0) {
            this.buffer.getItem(fuelSlot).shrink(fuelNeed);
        }
        List<ItemStack> payload = new ArrayList<>();
        for (int slot = 0; slot < this.buffer.getContainerSize(); slot++) {
            ItemStack stack = this.buffer.getItem(slot);
            if (!stack.isEmpty() && !stack.is(ROCKET_FUEL)) {
                payload.add(stack.copy());
                this.buffer.setItem(slot, ItemStack.EMPTY);
            }
        }
        ShipmentManager.ship(server, payload, dest.dimension(), target,
                RouteProviders.get().transitTicks(server, dest));
        LogisticsMetrics.recordShipmentLaunched(level);
        LogisticsMetrics.recordPlayerShipment(server, this.owner); // no-op unless attribution opted in
        setChanged();
    }

    private int findFuel(int min) {
        for (int slot = 0; slot < this.buffer.getContainerSize(); slot++) {
            ItemStack stack = this.buffer.getItem(slot);
            if (stack.is(ROCKET_FUEL) && stack.getCount() >= min) {
                return slot;
            }
        }
        return -1;
    }
}
