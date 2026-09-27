package za.co.neroland.nerologistics.conduit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.ship.DestinationKey;
import za.co.neroland.nerologistics.ship.LaunchBackoff;
import za.co.neroland.nerologistics.ship.LaunchResult;
import za.co.neroland.nerologistics.ship.PortSchedule;
import za.co.neroland.nerologistics.ship.RocketFlightsState;
import za.co.neroland.nerologistics.ship.RouteDestination;
import za.co.neroland.nerologistics.ship.RouteProvider;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShipDenial;
import za.co.neroland.nerologistics.ship.ShipOrigin;
import za.co.neroland.nerologistics.ship.ShippingClass;
import za.co.neroland.nerologistics.world.AttributionOptOutState;
import za.co.neroland.nerologistics.world.ErasedOwnersState;
import za.co.neroland.nerologistics.world.SavedDataRecovery;

/**
 * Rocket cargo port: buffers cargo, draws energy from cables, and on an interval launches its cargo along
 * the active {@link RouteProvider}:
 *
 * <ul>
 *   <li><b>Stub</b> (Nerospace absent / {@code nerospaceRouting=false}): to a same-channel port in the
 *       selected destination <em>dimension</em>, paying with {@code nerologistics:rocket_fuel}-tagged items —
 *       unchanged from earlier versions.</li>
 *   <li><b>Nerospace</b>: as a real Nerospace cargo flight from the Cargo Pad this port touches to the
 *       selected destination <em>pad or station</em>. The docked rocket carries the fuel; the port pays
 *       energy only and acts on behalf of its <b>dispatcher</b>.</li>
 * </ul>
 *
 * <p>The destination is persisted by stable identity ({@link DestinationKey}); pre-0.4 saves carrying the
 * old {@code DestIndex} are resolved once against the stub list and rewritten. A refused launch keeps the
 * cargo, records the {@link ShipDenial} (shown in the status line) and backs off exponentially up to
 * {@code shipDenialBackoffMaxTicks}; any player interaction resets the backoff.</p>
 *
 * <p><b>POPIA/GDPR.</b> Two UUID fields, with different purposes:</p>
 * <ul>
 *   <li>{@code owner} — opt-in <em>analytics</em> attribution; written only while
 *       {@code perPlayerThroughputAttribution} is on (default off).</li>
 *   <li>{@code dispatcher} — <em>functionally necessary</em> for Nerospace routing: Nerospace launches only
 *       on behalf of a player. Set when a player picks a Nerospace destination on the port (lawful basis:
 *       performing the function that player asked for), UUID only, never logged, shown to players only as
 *       "you" / "someone else", kept for the life of the block (breaking the port deletes it) and erased
 *       through Core's {@code PlayerDataErasure} (loaded ports immediately, unloaded ports via the
 *       {@link ErasedOwnersState} tombstones on their next load). An erased dispatcher leaves the port listing
 *       public pads only and unable to launch until someone configures it again.</li>
 * </ul>
 */
public class RocketCargoPortBlockEntity extends AbstractTerminalBlockEntity {

    public static final int BUFFER_SIZE = 10;
    public static final int ENERGY_CAPACITY = 500_000;
    public static final int ENERGY_MAX_IO = 8_000;

    /** Rocket fuel is matched by tag so any mod's fuel (incl. Nerospace's) can power a stub launch. */
    public static final TagKey<Item> ROCKET_FUEL = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "rocket_fuel"));

    /**
     * In-memory index of the currently-loaded ports (server side only): erasure scrubs them immediately,
     * and the dashboard / {@code /nerologistics shipping} list nearby ports. Registered on the first server
     * tick, removed in {@link #setRemoved()}.
     */
    private static final Set<RocketCargoPortBlockEntity> LOADED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private int channel;
    /** Stable destination identity; {@code null} until chosen (or while a legacy index awaits migration). */
    @Nullable
    private DestinationKey destination;
    /** Pre-0.4 {@code DestIndex} awaiting one-time migration, or {@code -1}. */
    private int legacyDestIndex = -1;
    /** QoS lane (Stage 17); persisted by name, missing/unknown loads as STANDARD (backward compat). */
    private ShippingClass shippingClass = ShippingClass.STANDARD;
    /** When the port launches (interval / when full / on a redstone pulse). */
    private PortSchedule schedule = PortSchedule.EVERY_INTERVAL;
    /** MANUAL: a rising redstone edge arrived; launch on the next tick. Transient. */
    private boolean manualPending;
    /** Last seen redstone input (edge detection). Transient: re-read on the first neighbour update. */
    private boolean powered;
    /** Per-port return-empty toggle; {@code null} = follow the {@code shipReturnEmpty} config default. */
    @Nullable
    private Boolean returnEmpty;
    private boolean joined;
    /** Placing player's UUID — stored ONLY when per-player attribution is opted in (POPIA/GDPR). */
    @Nullable
    private UUID owner;
    /** The player this port ships as under Nerospace routing (see class doc). */
    @Nullable
    private UUID dispatcher;

    /** Last refusal (persisted by name + game tick so the status survives a restart); null = none. */
    @Nullable
    private ShipDenial lastDenial;
    private long lastDenialTick;
    /** Transient backoff: no attempt before this game tick; level = consecutive backed-off denials. */
    private long nextAttemptTick;
    private int backoffLevel;

    public RocketCargoPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROCKET_CARGO_PORT.get(), pos, state, BUFFER_SIZE, ENERGY_CAPACITY, ENERGY_MAX_IO);
    }

    // --- Configuration ---------------------------------------------------------

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
            za.co.neroland.nerologistics.ship.ShipmentManager.rechannelPort(this.level, this.worldPosition, old,
                    this.channel);
        }
        resetBackoff();
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
        resetBackoff();
        setChanged();
        return this.shippingClass;
    }

    public PortSchedule schedule() {
        return this.schedule;
    }

    /** Cycle EVERY_INTERVAL → WHEN_FULL → MANUAL; returns the new mode. */
    public PortSchedule cycleSchedule() {
        this.schedule = this.schedule.next();
        this.manualPending = false;
        resetBackoff();
        setChanged();
        return this.schedule;
    }

    /**
     * Redstone input changed (from the block's {@code neighborChanged}). In {@link PortSchedule#MANUAL}
     * a rising edge queues one launch attempt for the next tick; a pulse is a deliberate request, so it
     * also clears any backoff.
     */
    public void onRedstone(boolean nowPowered) {
        boolean rising = nowPowered && !this.powered;
        this.powered = nowPowered;
        if (rising && this.schedule == PortSchedule.MANUAL) {
            this.manualPending = true;
            resetBackoff();
        }
    }

    /** Whether the rocket flies back empty after unloading (Nerospace routing). */
    public boolean returnEmpty() {
        return this.returnEmpty != null ? this.returnEmpty : NeroLogisticsConfig.shipReturnEmpty();
    }

    public boolean toggleReturnEmpty() {
        this.returnEmpty = !returnEmpty();
        resetBackoff();
        setChanged();
        return this.returnEmpty;
    }

    /** Whether at least {@code min} rocket-fuel-tagged items sit in the buffer (processor status dots). */
    public boolean hasFuelBuffered(int min) {
        for (int slot = 0; slot < this.buffer.getContainerSize(); slot++) {
            ItemStack stack = this.buffer.getItem(slot);
            if (stack.is(ROCKET_FUEL) && stack.getCount() >= min) {
                return true;
            }
        }
        return false;
    }

    /** The last refusal, or null when the last attempt launched (or none has been made). */
    @Nullable
    public ShipDenial lastDenial() {
        return this.lastDenial;
    }

    /** Whether a shipment this port launched (stub manifest or Nerospace flight) is still in transit. */
    public boolean hasFlightInTransit(MinecraftServer server) {
        if (this.level == null) {
            return false;
        }
        // RocketFlightsState only ever holds entries with Nerospace bound; don't create its file otherwise.
        return (RouteProviders.nerospaceBound()
                && RocketFlightsState.get(server).hasFlightFrom(this.level.dimension(), this.worldPosition))
                || za.co.neroland.nerologistics.ship.ShipmentState.get(server).hasShipmentFrom(
                        this.level.dimension(), this.worldPosition);
    }

    private ShipOrigin origin(ServerLevel level) {
        return new ShipOrigin(level, this.worldPosition, this.channel, this.dispatcher);
    }

    /**
     * Cycle to the next destination on behalf of {@code player}. Under Nerospace routing the player becomes
     * the port's dispatcher and the list holds the pads and stations <em>they</em> may ship to.
     *
     * @return the new destination, or empty when there is none to pick
     */
    public Optional<RouteDestination> cycleDestination(ServerLevel level, UUID player) {
        MinecraftServer server = level.getServer();
        RouteProvider provider = RouteProviders.get();
        if (provider.needsDispatcher() && !player.equals(this.dispatcher)) {
            this.dispatcher = player; // functionally necessary — see class doc (POPIA/GDPR)
            setChanged();
        }
        List<RouteDestination> dests = provider.destinations(server, origin(level));
        if (dests.isEmpty()) {
            return Optional.empty();
        }
        migrateLegacyDestination(server, level);
        int current = -1;
        for (int i = 0; i < dests.size(); i++) {
            if (dests.get(i).key().equals(this.destination)) {
                current = i;
                break;
            }
        }
        RouteDestination next = dests.get((current + 1) % dests.size());
        this.destination = next.key();
        this.legacyDestIndex = -1;
        resetBackoff();
        setChanged();
        return Optional.of(next);
    }

    /** Resolve a pre-0.4 {@code DestIndex} against the stub list once, then persist the stable key instead. */
    private void migrateLegacyDestination(MinecraftServer server, ServerLevel level) {
        if (this.destination != null || this.legacyDestIndex < 0) {
            return;
        }
        List<DestinationKey> stub = RouteProviders.stub().destinations(server, origin(level)).stream()
                .map(RouteDestination::key).toList();
        this.destination = DestinationKey.fromLegacyIndex(this.legacyDestIndex, stub).orElse(null);
        this.legacyDestIndex = -1;
        setChanged();
    }

    private void resetBackoff() {
        this.backoffLevel = 0;
        this.nextAttemptTick = 0L;
    }

    // --- Status ----------------------------------------------------------------

    /**
     * The port's status for chat: destination, lane, provider-specific origin/dispatcher details and the
     * last refusal. The dispatcher is only ever described relative to {@code viewer} ("you" / "someone
     * else" / "nobody") — never by name or UUID.
     */
    public List<Component> statusLines(ServerLevel level, @Nullable UUID viewer) {
        MinecraftServer server = level.getServer();
        RouteProvider provider = RouteProviders.get();
        ShipOrigin origin = origin(level);
        List<Component> out = new ArrayList<>();
        migrateLegacyDestination(server, level);
        Component dest = this.destination == null ? Component.translatable("nerologistics.ship.status.none")
                : provider.resolve(server, origin, this.destination).map(RouteDestination::displayName)
                        .orElse(Component.translatable("nerologistics.ship.status.unavailable"));
        out.add(Component.translatable("nerologistics.ship.status.destination", dest,
                Component.translatable(effectiveShippingClass().translationKey()),
                Component.translatable(this.schedule.translationKey())));
        if (provider.needsDispatcher()) {
            Component pad = provider.originName(server, origin).<Component>map(Component::literal)
                    .orElse(Component.translatable("nerologistics.ship.status.no_pad"));
            String who = this.dispatcher == null ? "nobody"
                    : this.dispatcher.equals(viewer) ? "you" : "someone_else";
            out.add(Component.translatable("nerologistics.ship.status.nerospace", pad,
                    Component.translatable("nerologistics.ship.status.dispatcher." + who),
                    Component.translatable(returnEmpty() ? "nerologistics.ship.status.on"
                            : "nerologistics.ship.status.off")));
        } else {
            out.add(Component.translatable("nerologistics.ship.status.stub", this.channel));
        }
        if (this.lastDenial != null) {
            long now = level.getGameTime();
            MutableComponent line = Component.translatable("nerologistics.ship.status.denied",
                    Component.translatable(this.lastDenial.translationKey()),
                    Math.max(0L, (now - this.lastDenialTick) / 20L));
            if (this.nextAttemptTick > now) {
                line.append(Component.translatable("nerologistics.ship.status.retry",
                        (this.nextAttemptTick - now + 19L) / 20L));
            }
            out.add(line);
        } else {
            out.add(Component.translatable("nerologistics.ship.status.ok"));
        }
        return out;
    }

    /** Loaded ports in {@code level} within {@code radius} blocks of {@code centre} (proximity-scoped). */
    public static List<RocketCargoPortBlockEntity> loadedNear(Level level, BlockPos centre, int radius) {
        long r2 = (long) radius * radius;
        List<RocketCargoPortBlockEntity> out = new ArrayList<>();
        for (RocketCargoPortBlockEntity port : LOADED) {
            if (port.level == level && port.worldPosition.distSqr(centre) <= r2) {
                out.add(port);
            }
        }
        out.sort((a, b) -> Double.compare(a.worldPosition.distSqr(centre), b.worldPosition.distSqr(centre)));
        return out;
    }

    // --- Persistence -------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Channel", this.channel);
        if (this.destination != null) {
            output.putString("Dest", this.destination.serialize());
        } else if (this.legacyDestIndex >= 0) {
            output.putInt("DestIndex", this.legacyDestIndex); // migration still pending — keep the old index
        }
        output.putString("ShipClass", this.shippingClass.name());
        output.putString("Schedule", this.schedule.name());
        output.putInt("ReturnEmpty", this.returnEmpty == null ? -1 : this.returnEmpty ? 1 : 0);
        if (this.lastDenial != null) {
            output.putString("LastDenial", this.lastDenial.name());
            output.putLong("LastDenialTick", this.lastDenialTick);
        }
        // POPIA/GDPR data minimisation: the owner UUID is only ever written while per-player
        // attribution is opted in; with attribution OFF (the default) no analytics data is persisted.
        boolean writeOwner = this.owner != null && NeroLogisticsConfig.perPlayerThroughputAttribution();
        output.putLong("OwnerMost", writeOwner ? this.owner.getMostSignificantBits() : 0L);
        output.putLong("OwnerLeast", writeOwner ? this.owner.getLeastSignificantBits() : 0L);
        // The dispatcher is functionally necessary for Nerospace routing (see class doc) — UUID only.
        output.putLong("DispatcherMost", this.dispatcher == null ? 0L : this.dispatcher.getMostSignificantBits());
        output.putLong("DispatcherLeast", this.dispatcher == null ? 0L : this.dispatcher.getLeastSignificantBits());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.channel = input.getIntOr("Channel", 0);
        this.destination = DestinationKey.parse(input.getStringOr("Dest", "")).orElse(null);
        // Pre-0.4 saves: only DestIndex (always written, default 0). Resolved once on first use.
        this.legacyDestIndex = this.destination == null ? input.getIntOr("DestIndex", -1) : -1;
        // Ports saved before QoS lanes existed have no ShipClass entry: they load as STANDARD.
        this.shippingClass = ShippingClass.byName(input.getStringOr("ShipClass", ShippingClass.STANDARD.name()));
        // Ports saved before scheduling existed launch on the interval, as they always did.
        this.schedule = PortSchedule.byName(input.getStringOr("Schedule", PortSchedule.EVERY_INTERVAL.name()));
        int re = input.getIntOr("ReturnEmpty", -1);
        this.returnEmpty = re < 0 ? null : re == 1;
        String denial = input.getStringOr("LastDenial", "");
        this.lastDenial = denial.isEmpty() ? null : ShipDenial.byName(denial);
        this.lastDenialTick = input.getLongOr("LastDenialTick", 0L);
        this.owner = readUuid(input, "OwnerMost", "OwnerLeast");
        if (this.owner != null && !NeroLogisticsConfig.perPlayerThroughputAttribution()) {
            // Attribution was switched off since this port was saved: drop the stale UUID now and,
            // per saveAdditional, never write it again.
            this.owner = null;
        }
        this.dispatcher = readUuid(input, "DispatcherMost", "DispatcherLeast");
    }

    @Nullable
    private static UUID readUuid(ValueInput input, String mostKey, String leastKey) {
        long most = input.getLongOr(mostKey, 0L);
        long least = input.getLongOr(leastKey, 0L);
        return (most == 0L && least == 0L) ? null : new UUID(most, least);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
        if (this.level != null && !this.level.isClientSide()) {
            za.co.neroland.nerologistics.ship.ShipmentManager.unregisterPort(this.level, this.worldPosition,
                    this.channel);
        }
    }

    // --- Tick ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, RocketCargoPortBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!be.joined) {
            za.co.neroland.nerologistics.ship.ShipmentManager.registerPort(level, pos, be.channel);
            LOADED.add(be);
            // Deferred erasure: this port may have been unloaded when its owner/dispatcher asked to be
            // erased — consult the tombstone list on first tick after load and scrub if so (POPIA/GDPR).
            if (be.owner != null || be.dispatcher != null) {
                ErasedOwnersState tombstones = ErasedOwnersState.get(serverLevel.getServer());
                if (be.owner != null && (tombstones.contains(be.owner)
                        || AttributionOptOutState.get(serverLevel.getServer()).isOptedOut(be.owner))) {
                    be.owner = null;
                    be.setChanged();
                }
                if (be.dispatcher != null && tombstones.contains(be.dispatcher)) {
                    be.dispatcher = null;
                    be.setChanged();
                }
            }
            be.joined = true;
        }
        if (be.schedule == PortSchedule.MANUAL) {
            // No interval work at all: only a queued redstone pulse launches.
            if (be.manualPending) {
                be.manualPending = false;
                be.tryShip(serverLevel, pos);
            }
            return;
        }
        int interval = NeroLogisticsConfig.shipIntervalTicks();
        long phase = level.getGameTime() % interval;
        // The stub launches on phase 0 as it always has; under Nerospace routing the lanes stagger so the
        // EXPRESS port gets a shared pad's rocket first (see ShippingClass#launchPhase).
        int want = RouteProviders.get().needsDispatcher() ? be.effectiveShippingClass().launchPhase(interval) : 0;
        if (phase == want) {
            be.tryShip(serverLevel, pos);
        }
    }

    /**
     * POPIA/GDPR erasure for rocket cargo ports, registered with Core's {@code PlayerDataErasure} in
     * {@code NeroLogisticsCommon.init()}. Scrubs both the opt-in attribution {@code owner} and the
     * {@code dispatcher}. Two-step, so one request reaches every port:
     *
     * <ol>
     *   <li><b>Loaded ports</b> (the in-memory {@link #LOADED} index) are scrubbed immediately.</li>
     *   <li>The UUID is recorded in the {@link ErasedOwnersState} tombstone list (durable SavedData,
     *       loaded through the guarded {@code SavedDataRecovery} accessor); any port that was
     *       <b>unloaded</b> right now scrubs itself against that list on its next load
     *       (see {@code serverTick}).</li>
     * </ol>
     */
    public static void erasePlayer(MinecraftServer server, UUID player) {
        for (RocketCargoPortBlockEntity port : LOADED) {
            boolean changed = false;
            if (player.equals(port.owner)) {
                port.owner = null;
                changed = true;
            }
            if (player.equals(port.dispatcher)) {
                port.dispatcher = null;
                changed = true;
            }
            if (changed) {
                port.setChanged();
            }
        }
        ErasedOwnersState tombstones = ErasedOwnersState.get(server);
        tombstones.add(player);
        // Push the change into the recovery backup right away so an erasure never lags there.
        SavedDataRecovery.backupNow(server.overworld(), ErasedOwnersState.TYPE, tombstones,
                ErasedOwnersState.ID.toString());
    }

    /**
     * {@code /nerologistics privacy optout}: scrub the analytics owner from every loaded port (unloaded
     * ports scrub themselves on their next load against the opt-out set). The dispatcher is untouched —
     * it is not analytics, and removing it would stop the port working.
     */
    public static void optOutAttribution(UUID player) {
        for (RocketCargoPortBlockEntity port : LOADED) {
            if (player.equals(port.owner)) {
                port.owner = null;
                port.setChanged();
            }
        }
    }

    /** Drop the loaded-port index (called from the server-stopped reset hook). */
    public static void clearAll() {
        LOADED.clear();
    }

    private void recordDenial(long now, ShipDenial denial) {
        boolean changed = this.lastDenial != denial;
        this.lastDenial = denial;
        this.lastDenialTick = changed ? now : this.lastDenialTick;
        if (denial.backsOff()) {
            this.nextAttemptTick = now + LaunchBackoff.delayTicks(this.backoffLevel,
                    NeroLogisticsConfig.shipIntervalTicks(), NeroLogisticsConfig.shipDenialBackoffMaxTicks());
            this.backoffLevel = LaunchBackoff.nextLevel(this.backoffLevel);
        }
        if (changed) {
            setChanged();
        }
    }

    private void tryShip(ServerLevel level, BlockPos pos) {
        if (!NeroLogisticsConfig.enableCrossDimension()) {
            return;
        }
        long now = level.getGameTime();
        if (now < this.nextAttemptTick) {
            return;
        }
        MinecraftServer server = level.getServer();
        RouteProvider provider = RouteProviders.get();
        // The only pass over the buffer: cargo slots, fullness, and (stub path) the biggest fuel stack —
        // fuel-tagged items are fuel, not cargo, when the port pays its own fuel.
        boolean ownFuel = provider.chargesOwnFuel();
        int size = this.buffer.getContainerSize();
        int[] cargoSlots = new int[size];
        int stacks = 0;
        boolean full = true;
        int fuelSlot = -1;
        int fuelCount = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = this.buffer.getItem(slot);
            if (stack.isEmpty()) {
                full = false;
            } else if (ownFuel && stack.is(ROCKET_FUEL)) {
                if (stack.getCount() > fuelCount) {
                    fuelSlot = slot;
                    fuelCount = stack.getCount();
                }
            } else {
                cargoSlots[stacks++] = slot;
            }
        }
        if (stacks == 0) {
            return; // nothing to ship — not a refusal
        }
        ShippingClass qos = effectiveShippingClass();
        if (!full && (this.schedule == PortSchedule.WHEN_FULL || (!ownFuel && qos == ShippingClass.BULK))) {
            return; // WHEN_FULL — and BULK under Nerospace routing (one rocket, one full load) — wait
        }
        if (provider.atCapacity(server)) {
            recordDenial(now, ShipDenial.AT_CAPACITY);
            return;
        }
        ShipOrigin origin = origin(level);
        migrateLegacyDestination(server, level);
        Optional<RouteDestination> dest = this.destination == null ? Optional.empty()
                : provider.resolve(server, origin, this.destination);
        if (dest.isEmpty()) {
            recordDenial(now, ShipDenial.NO_DESTINATION);
            return;
        }
        ShipDenial originProblem = provider.originProblem(server, origin);
        if (originProblem != null) {
            recordDenial(now, originProblem);
            return;
        }
        int baseEnergy = stacks * NeroLogisticsConfig.shipEnergyPerStack();
        int energyCost = ownFuel ? baseEnergy : qos.applyEnergy(baseEnergy);
        if (this.energy.getAmount() < energyCost) {
            recordDenial(now, ShipDenial.NO_ENERGY);
            return;
        }
        // Stub only: fuel items priced per launch (then scaled by the lane), paid from one stack.
        int fuelNeed = ownFuel ? qos.applyFuel(provider.fuelItems(server, origin, dest.get())) : 0;
        if (fuelNeed > 0 && fuelCount < fuelNeed) {
            recordDenial(now, ShipDenial.NO_FUEL_ITEMS);
            return;
        }
        List<ItemStack> payload = new ArrayList<>(stacks);
        for (int i = 0; i < stacks; i++) {
            payload.add(this.buffer.getItem(cargoSlots[i]).copy());
        }
        LaunchResult result = provider.launch(server, origin, dest.get(), payload, qos, returnEmpty());
        if (!result.isAccepted()) {
            recordDenial(now, result.denial());
            return;
        }
        // Accepted: the provider owns the cargo now. Charge energy (+ stub fuel) and clear the slots.
        this.energy.consume(energyCost);
        if (fuelNeed > 0) {
            this.buffer.getItem(fuelSlot).shrink(fuelNeed);
        }
        for (int i = 0; i < stacks; i++) {
            this.buffer.setItem(cargoSlots[i], ItemStack.EMPTY);
        }
        LogisticsMetrics.recordPlayerShipment(server, this.owner); // no-op unless attribution opted in
        this.lastDenial = null;
        resetBackoff();
        setChanged();
    }
}
