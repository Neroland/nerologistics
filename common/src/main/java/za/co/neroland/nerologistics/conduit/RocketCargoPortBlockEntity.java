package za.co.neroland.nerologistics.conduit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.ship.RouteDestination;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShipmentManager;
import za.co.neroland.nerologistics.ship.ShippingClass;
import za.co.neroland.nerologistics.world.ErasedOwnersState;
import za.co.neroland.nerologistics.world.SavedDataRecovery;

/**
 * Rocket cargo port: buffers cargo, draws energy from cables, and on an interval launches a
 * cross-dimension shipment of its non-fuel cargo to a same-channel port in the selected destination
 * dimension. Deliberately slow and energy-hungry; consumes rocket fuel <em>by tag</em>
 * ({@code nerologistics:rocket_fuel}) rather than any Nerospace class; gated behind
 * fuel. Destinations come from the {@link RouteProviders} seam — the stub (every loaded dimension)
 * standalone, or Nerospace's planet/station catalog when {@code compat.NerospaceCompat} bound it.
 * Right-click cycles destination; sneak-right-click cycles channel.
 */
public class RocketCargoPortBlockEntity extends AbstractTerminalBlockEntity {

    public static final int BUFFER_SIZE = 10;
    public static final int ENERGY_CAPACITY = 500_000;
    public static final int ENERGY_MAX_IO = 8_000;

    /** Rocket fuel is matched by tag so any mod's fuel (incl. Nerospace's) can power a launch. */
    public static final TagKey<Item> ROCKET_FUEL = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "rocket_fuel"));

    /**
     * Lightweight in-memory index of the currently-loaded ports (server side only), so a
     * POPIA/GDPR erasure request can scrub the owner UUID from every loaded port immediately.
     * Registered on the port's first server tick, unregistered in {@link #setRemoved()}; unloaded
     * ports are covered by the {@link ErasedOwnersState} tombstone consulted on their next load.
     */
    private static final Set<RocketCargoPortBlockEntity> LOADED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private int channel;
    private int destIndex;
    /** QoS lane (Stage 17); persisted by name, missing/unknown loads as STANDARD (backward compat). */
    private ShippingClass shippingClass = ShippingClass.STANDARD;
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

    /** The configured QoS class as set by the player (may be masked by {@code enableShippingQos=false}). */
    public ShippingClass shippingClass() {
        return this.shippingClass;
    }

    /** The class actually applied to launches: STANDARD whenever the QoS toggle is off (clean degrade). */
    public ShippingClass effectiveShippingClass() {
        return NeroLogisticsConfig.enableShippingQos() ? this.shippingClass : ShippingClass.STANDARD;
    }

    /** Cycle STANDARD → EXPRESS → BULK; returns the new class. */
    public ShippingClass cycleShippingClass() {
        this.shippingClass = this.shippingClass.next();
        setChanged();
        return this.shippingClass;
    }

    /** Whether at least {@code min} rocket-fuel-tagged items sit in the buffer (processor status dots). */
    public boolean hasFuelBuffered(int min) {
        return findFuel(min) >= 0;
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
        output.putString("ShipClass", this.shippingClass.name());
        // POPIA/GDPR data minimisation: the owner UUID is only ever written while per-player
        // attribution is opted in; with attribution OFF (the default) no personal data is persisted.
        boolean writeOwner = this.owner != null && NeroLogisticsConfig.perPlayerThroughputAttribution();
        output.putLong("OwnerMost", writeOwner ? this.owner.getMostSignificantBits() : 0L);
        output.putLong("OwnerLeast", writeOwner ? this.owner.getLeastSignificantBits() : 0L);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.channel = input.getIntOr("Channel", 0);
        this.destIndex = input.getIntOr("DestIndex", 0);
        // Ports saved before QoS lanes existed have no ShipClass entry: they load as STANDARD.
        this.shippingClass = ShippingClass.byName(input.getStringOr("ShipClass", ShippingClass.STANDARD.name()));
        long most = input.getLongOr("OwnerMost", 0L);
        long least = input.getLongOr("OwnerLeast", 0L);
        this.owner = (most == 0L && least == 0L) ? null : new UUID(most, least);
        if (this.owner != null && !NeroLogisticsConfig.perPlayerThroughputAttribution()) {
            // Attribution was switched off since this port was saved: drop the stale UUID now and,
            // per saveAdditional, never write it again.
            this.owner = null;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
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
            LOADED.add(be);
            // Deferred erasure: this port may have been unloaded when its owner asked to be erased —
            // consult the tombstone list on first tick after load and scrub if so (POPIA/GDPR).
            if (be.owner != null && ErasedOwnersState.get(serverLevel.getServer()).contains(be.owner)) {
                be.owner = null;
                be.setChanged();
            }
            be.joined = true;
        }
        if (level.getGameTime() % NeroLogisticsConfig.shipIntervalTicks() == 0L) {
            be.tryShip(serverLevel, pos);
        }
    }

    /**
     * POPIA/GDPR erasure for rocket cargo ports, registered with Core's {@code PlayerDataErasure} in
     * {@code NeroLogisticsCommon.init()}. Two-step, so one request reaches every port:
     *
     * <ol>
     *   <li><b>Loaded ports</b> (the in-memory {@link #LOADED} index) are scrubbed immediately.</li>
     *   <li>The UUID is recorded in the {@link ErasedOwnersState} tombstone list (durable SavedData,
     *       loaded through the guarded {@code SavedDataRecovery} accessor); any port that was
     *       <b>unloaded</b> right now scrubs itself against that list on its next load
     *       (see {@code serverTick}).</li>
     * </ol>
     *
     * <p>Note the belt-and-braces posture: with {@code perPlayerThroughputAttribution} OFF (the
     * default) no owner UUID is ever persisted in the first place, so this path only matters for
     * worlds that opted in.
     */
    public static void erasePlayer(MinecraftServer server, UUID player) {
        for (RocketCargoPortBlockEntity port : LOADED) {
            if (player.equals(port.owner)) {
                port.owner = null;
                port.setChanged();
            }
        }
        ErasedOwnersState tombstones = ErasedOwnersState.get(server);
        tombstones.add(player);
        // Push the change into the recovery backup right away so an erasure never lags there.
        SavedDataRecovery.backupNow(server.overworld(), ErasedOwnersState.TYPE, tombstones,
                ErasedOwnersState.ID.toString());
    }

    /** Drop the loaded-port index (called from the server-stopped reset hook). */
    public static void clearAll() {
        LOADED.clear();
    }

    private void tryShip(ServerLevel level, BlockPos pos) {
        if (!NeroLogisticsConfig.enableCrossDimension()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null || ShipmentManager.atCapacity(server)) {
            return;
        }
        List<RouteDestination> dests = RouteProviders.get().destinations(server);
        if (dests.isEmpty()) {
            return;
        }
        RouteDestination dest = dests.get(Math.floorMod(this.destIndex, dests.size()));
        if (!RouteProviders.get().isAvailable(server, level.dimension(), dest)) {
            return; // route closed (e.g. Nerospace: non-endpoint origin, or shipping to itself)
        }
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
        // QoS lane (STANDARD unless enableShippingQos): scales the route's fuel bill and transit time.
        ShippingClass qos = effectiveShippingClass();
        // Fuel is priced per route (Nerospace scales it with distance); the stub uses the flat config.
        int fuelNeed = qos.applyFuel(RouteProviders.get().fuelPerLaunch(server, level.dimension(), dest));
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
        ShipmentManager.ship(server, payload, level.dimension(), pos, dest.dimension(), target,
                qos.applyTransit(RouteProviders.get().transitTicks(server, level.dimension(), dest)));
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
