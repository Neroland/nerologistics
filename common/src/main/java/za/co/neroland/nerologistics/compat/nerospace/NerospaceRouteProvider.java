package za.co.neroland.nerologistics.compat.nerospace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.ship.DestinationKey;
import za.co.neroland.nerologistics.ship.FlightView;
import za.co.neroland.nerologistics.ship.LaunchResult;
import za.co.neroland.nerologistics.ship.RocketFlightsState;
import za.co.neroland.nerologistics.ship.RouteDestination;
import za.co.neroland.nerologistics.ship.RouteProvider;
import za.co.neroland.nerologistics.ship.RouteProviders;
import za.co.neroland.nerologistics.ship.ShipDenial;
import za.co.neroland.nerologistics.ship.ShipOrigin;
import za.co.neroland.nerologistics.ship.ShippingClass;
import za.co.neroland.nerospace.api.route.FlightHandle;
import za.co.neroland.nerospace.api.route.FlightRequest;
import za.co.neroland.nerospace.api.route.FlightRequestResult;
import za.co.neroland.nerospace.api.route.FlightState;
import za.co.neroland.nerospace.api.route.PadHandle;
import za.co.neroland.nerospace.api.route.RouteApi;
import za.co.neroland.nerospace.api.route.RouteEvents;
import za.co.neroland.nerospace.api.route.RouteQuote;

/**
 * {@link RouteProvider} over Nerospace's semver-stable route API ({@code za.co.neroland.nerospace.api.route},
 * Nerospace 1.3.0+). Rocket cargo is a <b>real Nerospace flight</b>: the port must touch a Cargo Pad with a
 * docked, fuelled Cargo Rocket; Nerospace draws the fuel, persists the flight across restarts, holds it
 * when the destination is unavailable and crates the cargo after its timeout. NeroLogistics only supplies
 * the manifest and remembers which flights are its own ({@link RocketFlightsState}).
 *
 * <p>This is the only package that imports Nerospace. It is reached solely through {@link #install()},
 * which {@code compat.NerospaceCompat} calls behind its {@code isModLoaded("nerospace")} guard, so the JVM
 * never loads a Nerospace class when Nerospace is absent.</p>
 *
 * <p>POPIA/GDPR: every call acts as the port's dispatcher (a UUID the caller passes in, never stored
 * here). The origin-pad cache keeps pad <em>ids</em> only — never another player's pad position. Logs
 * carry flight/pad ids only.</p>
 */
public final class NerospaceRouteProvider implements RouteProvider {

    /** A UUID that owns nothing: lists only public pads for a port nobody has configured. */
    private static final UUID NOBODY = new UUID(0L, 0L);

    /** Ticks between reconciliations of our flight store against Nerospace (covers missed events). */
    private static final int RECONCILE_INTERVAL = 200;

    private final NerospaceFlightListener listener = new NerospaceFlightListener();

    /** Port (dimension → packed position) → origin pad id. Ids only; re-validated before every use. */
    private final Map<ResourceKey<Level>, Map<Long, Integer>> originPads = new HashMap<>();

    private final AtomicBoolean warned = new AtomicBoolean();

    private NerospaceRouteProvider() {
    }

    /**
     * Bind the provider and subscribe its event listener. Throws {@link LinkageError} when the loaded
     * Nerospace predates the route API — {@code NerospaceCompat} catches that and keeps the stub.
     */
    public static void install() {
        RouteApi.instance(); // first touch: fails fast here on an API-less Nerospace
        NerospaceRouteProvider provider = new NerospaceRouteProvider();
        RouteEvents.subscribe(provider.listener);
        RouteProviders.bindNerospace(provider);
    }

    private static RouteApi api() {
        return RouteApi.instance();
    }

    @Override
    public String id() {
        return "nerospace";
    }

    @Override
    public boolean chargesOwnFuel() {
        return false; // the docked rocket carries the fuel
    }

    @Override
    public boolean needsDispatcher() {
        return true;
    }

    // --- Origin pad ----------------------------------------------------------

    /** The Cargo Pad this port touches and its dispatcher may launch from, if any. */
    private Optional<PadHandle> originPad(MinecraftServer server, ShipOrigin origin) {
        UUID dispatcher = origin.dispatcher();
        if (dispatcher == null) {
            return Optional.empty();
        }
        ResourceKey<Level> dim = origin.level().dimension();
        Map<Long, Integer> byPos = this.originPads.computeIfAbsent(dim, k -> new HashMap<>());
        long packed = origin.pos().asLong();
        Integer cached = byPos.get(packed);
        if (cached != null) {
            Optional<PadHandle> pad = api().pad(server, cached);
            if (pad.isPresent() && usableOrigin(pad.get(), dim, origin.pos(), dispatcher)) {
                return pad;
            }
            byPos.remove(packed);
        }
        for (PadHandle pad : api().padsVisibleTo(server, dispatcher)) {
            if (usableOrigin(pad, dim, origin.pos(), dispatcher)) {
                byPos.put(packed, pad.id());
                return Optional.of(pad);
            }
        }
        return Optional.empty();
    }

    /** A placed (non-station) pad in the port's dimension, touching it (26-neighbourhood), usable by the dispatcher. */
    private static boolean usableOrigin(PadHandle pad, ResourceKey<Level> dim, BlockPos port, UUID dispatcher) {
        if (pad.isStation() || !pad.dimension().equals(dim)) {
            return false;
        }
        BlockPos p = pad.position();
        boolean touching = Math.abs(p.getX() - port.getX()) <= 1 && Math.abs(p.getY() - port.getY()) <= 1
                && Math.abs(p.getZ() - port.getZ()) <= 1;
        return touching && (pad.ownedBy(dispatcher) || pad.accessibleBy(dispatcher));
    }

    @Override
    @Nullable
    public ShipDenial originProblem(MinecraftServer server, ShipOrigin origin) {
        if (origin.dispatcher() == null) {
            return ShipDenial.NO_DISPATCHER;
        }
        return guarded(() -> originPad(server, origin).isPresent() ? null : ShipDenial.NO_ORIGIN_PAD,
                ShipDenial.OTHER);
    }

    @Override
    public Optional<String> originName(MinecraftServer server, ShipOrigin origin) {
        return guarded(() -> originPad(server, origin).map(PadHandle::name), Optional.empty());
    }

    // --- Destinations --------------------------------------------------------

    @Override
    public List<RouteDestination> destinations(MinecraftServer server, ShipOrigin origin) {
        return guarded(() -> {
            UUID viewer = origin.dispatcher();
            int originId = originPad(server, origin).map(PadHandle::id).orElse(Integer.MIN_VALUE);
            List<PadHandle> pads = new ArrayList<>(api().padsVisibleTo(server, viewer == null ? NOBODY : viewer));
            pads.sort(Comparator.comparing(PadHandle::isStation).thenComparingInt(PadHandle::id));
            List<RouteDestination> out = new ArrayList<>(pads.size());
            for (PadHandle pad : pads) {
                if (pad.id() == originId || (viewer == null && !pad.isPublic())) {
                    continue;
                }
                out.add(RouteDestination.ofPad(pad.id(), pad.name(), pad.dimension(), pad.isStation()));
            }
            return List.copyOf(out);
        }, List.of());
    }

    @Override
    public Optional<RouteDestination> resolve(MinecraftServer server, ShipOrigin origin, DestinationKey key) {
        Optional<Integer> padId = key.padId();
        if (padId.isEmpty()) {
            return Optional.empty(); // a stub dimension key: not a Nerospace destination
        }
        return guarded(() -> api().pad(server, padId.get())
                .filter(pad -> origin.dispatcher() == null ? pad.isPublic() : pad.accessibleBy(origin.dispatcher()))
                .map(pad -> RouteDestination.ofPad(pad.id(), pad.name(), pad.dimension(), pad.isStation())),
                Optional.empty());
    }

    @Override
    public Optional<Integer> estimateTicks(MinecraftServer server, ShipOrigin origin, RouteDestination destination,
            List<ItemStack> payload, boolean returnEmpty) {
        return guarded(() -> {
            Optional<PadHandle> from = originPad(server, origin);
            Optional<Integer> to = destination.key().padId();
            if (from.isEmpty() || to.isEmpty()) {
                return Optional.<Integer>empty();
            }
            return api().quote(server, from.get().id(), to.get(), payload, returnEmpty).map(RouteQuote::travelTicks);
        }, Optional.empty());
    }

    // --- Launch --------------------------------------------------------------

    @Override
    public LaunchResult launch(MinecraftServer server, ShipOrigin origin, RouteDestination destination,
            List<ItemStack> payload, ShippingClass qos, boolean returnEmpty) {
        UUID dispatcher = origin.dispatcher();
        if (dispatcher == null) {
            return LaunchResult.denied(ShipDenial.NO_DISPATCHER);
        }
        Optional<Integer> destId = destination.key().padId();
        if (destId.isEmpty()) {
            return LaunchResult.denied(ShipDenial.NO_DESTINATION);
        }
        return guarded(() -> {
            Optional<PadHandle> from = originPad(server, origin);
            if (from.isEmpty()) {
                return LaunchResult.denied(ShipDenial.NO_ORIGIN_PAD);
            }
            int originId = from.get().id();
            if (originId == destId.get()) {
                return LaunchResult.denied(ShipDenial.NO_DESTINATION);
            }
            // Quote first: an empty quote means no route at all (unknown id, station origin) — no need to
            // ask for a flight. Travel time and fuel are Nerospace's; lanes never scale them (§ wiki).
            if (api().quote(server, originId, destId.get(), payload, returnEmpty).isEmpty()) {
                return LaunchResult.denied(ShipDenial.ROUTE_CLOSED);
            }
            FlightRequestResult result = api().requestFlight(server, dispatcher,
                    new FlightRequest(originId, destId.get(), payload, returnEmpty));
            if (result.accepted()) {
                FlightHandle flight = result.flight().orElseThrow();
                RocketFlightsState.get(server).track(new RocketFlightsState.Tracked(flight.id(),
                        origin.level().dimension(), origin.pos().immutable(), destId.get(),
                        server.overworld().getGameTime(), -1L));
                NeroLogisticsCommon.LOGGER.debug("[NeroLogistics] cargo flight {} requested (pad {} -> {})",
                        flight.id(), originId, destId.get());
                return LaunchResult.accepted(flight.id());
            }
            FlightRequestResult.Denial denial = result.denial();
            ShipDenial mapped = ShipDenial.byName(denial == null ? null : denial.name());
            if (mapped == ShipDenial.NO_ORIGIN || mapped == ShipDenial.NOT_PERMITTED) {
                forgetOrigin(origin); // re-resolve next time (pad broken / access list changed)
            }
            return LaunchResult.denied(mapped);
        }, LaunchResult.denied(ShipDenial.OTHER));
    }

    private void forgetOrigin(ShipOrigin origin) {
        Map<Long, Integer> byPos = this.originPads.get(origin.level().dimension());
        if (byPos != null) {
            byPos.remove(origin.pos().asLong());
        }
    }

    // --- In-transit views ----------------------------------------------------

    @Override
    public int inTransitCount(MinecraftServer server) {
        return RocketFlightsState.get(server).count();
    }

    @Override
    public List<FlightView> flightsNear(MinecraftServer server, ResourceKey<Level> dim, BlockPos centre, int radius) {
        RocketFlightsState state = RocketFlightsState.get(server);
        if (state.count() == 0) {
            return List.of();
        }
        long now = server.overworld().getGameTime();
        long r2 = (long) radius * radius;
        return guarded(() -> {
            List<FlightView> out = new ArrayList<>();
            for (RocketFlightsState.Tracked tracked : state.tracked()) {
                if (!tracked.originDim().equals(dim) || tracked.originPos().distSqr(centre) > r2) {
                    continue;
                }
                Optional<FlightHandle> flight = api().flight(server, tracked.flightId());
                if (flight.isEmpty()) {
                    continue;
                }
                FlightHandle f = flight.get();
                out.add(new FlightView(f.id(), padName(server, f.originPadId()), padName(server, f.destinationPadId()),
                        FlightView.State.byName(f.state().name()), f.arrivesAt() - now,
                        tracked.heldSince() >= 0 ? now - tracked.heldSince() : -1L));
            }
            return out;
        }, List.of());
    }

    /** A pad's player-facing label ("Station: …" for stations), or its bare id when it is gone. */
    private static String padName(MinecraftServer server, int padId) {
        return api().pad(server, padId).map(p -> p.isStation() ? "Station: " + p.name() : p.name())
                .orElse("#" + padId);
    }

    @Override
    public List<DropHint> recentDropsNear(MinecraftServer server, ResourceKey<Level> dim, BlockPos centre,
            int radius, @Nullable UUID viewer) {
        List<RocketFlightsState.Dropped> drops = RocketFlightsState.get(server).drops();
        if (drops.isEmpty()) {
            return List.of();
        }
        long now = server.overworld().getGameTime();
        long r2 = (long) radius * radius;
        List<DropHint> out = new ArrayList<>();
        for (RocketFlightsState.Dropped drop : drops) {
            if (drop.originDim().equals(dim) && drop.originPos().distSqr(centre) <= r2) {
                String where = dropHint(server, drop.destPadId(), viewer).orElse("#" + drop.destPadId());
                out.add(new DropHint(drop.flightId(), where, now - drop.tick()));
            }
        }
        return out;
    }

    /**
     * The drop-recovery hint for {@code viewer}: the destination pad's label, plus its position only when the
     * viewer may route to that pad anyway (so the hint never reveals a private pad's location). Nerospace
     * crates the cargo at the destination pad's position, so that is the crate position.
     */
    private Optional<String> dropHint(MinecraftServer server, int padId, @Nullable UUID viewer) {
        return guarded(() -> api().pad(server, padId).map(pad -> {
            String name = pad.isStation() ? "Station: " + pad.name() : pad.name();
            boolean mayKnow = pad.isPublic() || (viewer != null && pad.accessibleBy(viewer));
            if (!mayKnow) {
                return name;
            }
            BlockPos p = pad.position();
            return name + " @ " + p.getX() + " " + p.getY() + " " + p.getZ() + " ("
                    + pad.dimension().identifier().getPath() + ")";
        }), Optional.empty());
    }

    // --- Tick: event drain + reconciliation ------------------------------------

    @Override
    public void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        boolean reconcile = now % RECONCILE_INTERVAL == 0L;
        if (this.listener.isEmpty() && !reconcile) {
            return; // idle: no allocation, no SavedData lookup
        }
        guarded(() -> {
            drain(server, now);
            if (reconcile) {
                reconcile(server, now);
            }
            return null;
        }, null);
    }

    private void drain(MinecraftServer server, long now) {
        RocketFlightsState state = null;
        NerospaceFlightListener.Event event;
        while ((event = this.listener.poll()) != null) {
            if (event.kind() == NerospaceFlightListener.Kind.PAD_UNREGISTERED) {
                int padId = event.id();
                for (Map<Long, Integer> byPos : this.originPads.values()) {
                    byPos.values().removeIf(id -> id == padId);
                }
                continue;
            }
            if (state == null) {
                state = RocketFlightsState.get(server);
            }
            RocketFlightsState.Tracked tracked = state.get(event.id());
            if (tracked == null) {
                continue; // not a flight NeroLogistics requested
            }
            switch (event.kind()) {
                case DEPARTED -> LogisticsMetrics.recordShipmentLaunched(tracked.originDim());
                case ARRIVED -> {
                    state.untrack(tracked.flightId());
                    LogisticsMetrics.recordShipmentDelivered(destDim(server, tracked));
                }
                case HELD -> {
                    state.markHeld(tracked.flightId(), now);
                    LogisticsMetrics.recordShipmentHeld(destDim(server, tracked));
                }
                case DROPPED -> dropped(server, state, tracked, now);
                default -> {
                }
            }
        }
    }

    /** Catch up on anything the event queue missed (pruned flights, a drain that never ran). */
    private void reconcile(MinecraftServer server, long now) {
        RocketFlightsState state = RocketFlightsState.get(server);
        if (state.count() == 0) {
            return;
        }
        for (RocketFlightsState.Tracked tracked : state.tracked()) {
            Optional<FlightHandle> flight = api().flight(server, tracked.flightId());
            if (flight.isEmpty()) {
                state.untrack(tracked.flightId()); // pruned by Nerospace: long finished
                continue;
            }
            FlightState fs = flight.get().state();
            if (fs == FlightState.DELIVERED) {
                state.untrack(tracked.flightId());
                LogisticsMetrics.recordShipmentDelivered(destDim(server, tracked));
            } else if (fs == FlightState.DROPPED) {
                dropped(server, state, tracked, now);
            } else if (fs == FlightState.HOLDING && tracked.heldSince() < 0) {
                state.markHeld(tracked.flightId(), now);
            }
        }
    }

    private static void dropped(MinecraftServer server, RocketFlightsState state, RocketFlightsState.Tracked tracked,
            long now) {
        state.untrack(tracked.flightId());
        state.recordDrop(new RocketFlightsState.Dropped(tracked.flightId(), tracked.originDim(), tracked.originPos(),
                tracked.destPadId(), now));
        LogisticsMetrics.recordShipmentDropped(destDim(server, tracked));
        NeroLogisticsCommon.LOGGER.info("[NeroLogistics] cargo flight {} was crated at destination pad {}",
                tracked.flightId(), tracked.destPadId());
    }

    /** The destination pad's dimension for the per-dimension counters (the origin's when the pad is gone). */
    private static ResourceKey<Level> destDim(MinecraftServer server, RocketFlightsState.Tracked tracked) {
        return api().pad(server, tracked.destPadId()).map(PadHandle::dimension).orElse(tracked.originDim());
    }

    @Override
    public void reset() {
        this.originPads.clear();
        this.listener.clear();
    }

    // --- Failure isolation ----------------------------------------------------

    /** Run an API call; a runtime failure logs once (class name only) and yields {@code fallback}. */
    private <T> T guarded(java.util.function.Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            if (this.warned.compareAndSet(false, true)) {
                NeroLogisticsCommon.LOGGER.warn("[NeroLogistics] Nerospace route API call failed ({}); "
                        + "the port keeps its cargo and retries with backoff", e.getClass().getSimpleName());
            }
            return fallback;
        }
    }
}
